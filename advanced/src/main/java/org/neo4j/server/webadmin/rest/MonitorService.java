/**
 * Copyright (c) 2002-2011 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.server.webadmin.rest;

import java.util.Date;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.neo4j.server.rest.repr.OutputFormat;
import org.neo4j.server.rrd.RrdFactory;
import org.neo4j.server.webadmin.rest.representations.RrdDataRepresentation;
import org.neo4j.server.webadmin.rest.representations.ServiceDefinitionRepresentation;
import org.rrd4j.ConsolFun;
import org.rrd4j.core.FetchRequest;
import org.rrd4j.core.RrdDb;

/**
 * This exposes data from an internal round-robin database that tracks various
 * system KPIs over time.
 */
@Path( MonitorService.ROOT_PATH )
public class MonitorService implements AdvertisableService
{
    private final RrdDb rrdDb;
    private final OutputFormat output;

    public String getName()
    {
        return "monitor";
    }

    public String getServerPath()
    {
        return ROOT_PATH;
    }

    public MonitorService( @Context RrdDb rrdDb, @Context OutputFormat output )
    {
        this.rrdDb = rrdDb;
        this.output = output;
    }

    public static final String ROOT_PATH = "server/monitor";
    public static final String DATA_PATH = "/fetch";
    public static final String DATA_FROM_PATH = DATA_PATH + "/{start}";
    public static final String DATA_SPAN_PATH = DATA_PATH + "/{start}/{stop}";

    public static final long MAX_TIMESPAN = 1000l * 60l * 60l * 24l * 365l * 5;
    public static final long DEFAULT_TIMESPAN = 1000 * 60 * 60 * 24;


    @GET
    public Response getServiceDefinition()
    {
        ServiceDefinitionRepresentation sdr = new ServiceDefinitionRepresentation( ROOT_PATH );
        sdr.resourceTemplate( "data_from", MonitorService.DATA_FROM_PATH );
        sdr.resourceTemplate( "data_period", MonitorService.DATA_SPAN_PATH );
        sdr.resourceUri( "latest_data", MonitorService.DATA_PATH );

        return output.ok( sdr );
    }

    @GET
    @Path( DATA_PATH )
    public Response getData()
    {
        long time = new Date().getTime();
        return getData( time - DEFAULT_TIMESPAN, time );
    }

    @GET
    @Path( DATA_FROM_PATH )
    public Response getData( @PathParam( "start" ) long start )
    {
        return getData( start, new Date().getTime() );
    }

    @GET
    @Path( DATA_SPAN_PATH )
    public Response getData( @PathParam( "start" ) long start,
                             @PathParam( "stop" ) long stop )
    {
        if ( start >= stop || ( stop - start ) > MAX_TIMESPAN )
        {
            String message = String.format( "Start time must be before stop time, and the total time span can be no bigger than %dms. Time span was %dms.", MAX_TIMESPAN, ( stop - start ) );
            return output.badRequest( new IllegalArgumentException( message ) );
        }

        try
        {

            FetchRequest request = rrdDb.createFetchRequest(
                    ConsolFun.AVERAGE, start, stop,
                    getResolutionFor( stop - start ) );

            return output.ok( new RrdDataRepresentation( request.fetchData() ) );
        } catch ( Exception e )
        {
            return output.serverError( e );
        }
    }

    private long getResolutionFor( long timespan )
    {
        long preferred = (long)Math.floor( timespan / ( RrdFactory.STEPS_PER_ARCHIVE * 2 ) );

        // Don't allow resolutions smaller than the actual minimum resolution
        return preferred > RrdFactory.STEP_SIZE ? preferred : RrdFactory.STEP_SIZE;
    }
}

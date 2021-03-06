/*
 * Copyright (c) 2002-2018 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.api.index;

import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.neo4j.internal.kernel.api.IndexQuery;
import org.neo4j.internal.kernel.api.schema.SchemaDescriptor;
import org.neo4j.kernel.api.schema.index.TestIndexDescriptorFactory;
import org.neo4j.values.storable.RandomValues;
import org.neo4j.values.storable.Value;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.neo4j.helpers.collection.Iterables.single;
import static org.neo4j.kernel.api.index.IndexQueryHelper.add;

@Ignore( "Not a test. This is a compatibility suite that provides test cases for verifying" +
        " IndexProvider implementations. Each index provider that is to be tested by this suite" +
        " must create their own test class extending IndexProviderCompatibilityTestSuite." +
        " The @Ignore annotation doesn't prevent these tests to run, it rather removes some annoying" +
        " errors or warnings in some IDEs about test classes needing a public zero-arg constructor." )
public class SimpleRandomizedIndexAccessorCompatibility extends IndexAccessorCompatibility
{
    public SimpleRandomizedIndexAccessorCompatibility( IndexProviderCompatibilityTestSuite testSuite )
    {
        super( testSuite, TestIndexDescriptorFactory.forLabel( 1000, 100 ) );
    }

    @Test
    public void testExactMatchOnRandomValues() throws Exception
    {
        // given
        List<RandomValues.Type> types = testSuite.supportedValueTypes();
        Collections.shuffle( types, random.random() );
        types = types.subList( 0, random.nextInt( 2, types.size() ) );

        List<IndexEntryUpdate<?>> updates = new ArrayList<>();
        Set<Value> duplicateChecker = new HashSet<>();
        for ( long id = 0; id < 30_000; id++ )
        {
            IndexEntryUpdate<SchemaDescriptor> update;
            do
            {
                update = add( id, descriptor.schema(), random.nextValue( random.among( types ) ) );
            }
            while ( !duplicateChecker.add( update.values()[0] ) );
            updates.add( update );
        }
        updateAndCommit( updates );

        // when
        for ( IndexEntryUpdate<?> update : updates )
        {
            // then
            List<Long> hits = query( IndexQuery.exact( 0, update.values()[0] ) );
            assertEquals( hits.toString(), 1, hits.size() );
            assertThat( single( hits ), equalTo( update.getEntityId() ) );
        }
    }
}

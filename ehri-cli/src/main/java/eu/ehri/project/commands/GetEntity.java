/*
 * Copyright 2015 Data Archiving and Networked Services (an institute of
 * Koninklijke Nederlandse Akademie van Wetenschappen), King's College London,
 * Georg-August-Universitaet Goettingen Stiftung Oeffentlichen Rechts
 *
 * Licensed under the EUPL, Version 1.1 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.ehri.project.commands;

import com.tinkerpop.frames.FramedGraph;
import eu.ehri.project.core.GraphManager;
import eu.ehri.project.core.GraphManagerFactory;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.persistence.Serializer;
import org.apache.commons.cli.CommandLine;

/**
 * Fetch an item's serialized representation via the command line.
 */
public class GetEntity extends BaseCommand {

    final static String NAME = "get";

    public GetEntity() {
    }

    @Override
    public String getHelp() {
        return "Usage: get [OPTIONS] <type> <identifier>";
    }

    @Override
    public String getUsage() {
        return "Get an entity by its identifier.";
    }

    @Override
    @SuppressWarnings("unchecked")
    public int execWithOptions(FramedGraph<?> graph,
            CommandLine cmdLine) throws Exception {

        GraphManager manager = GraphManagerFactory.getInstance(graph);
        Serializer serializer = new Serializer(graph);

        if (cmdLine.getArgList().size() < 2)
            throw new RuntimeException(getHelp());

        EntityClass type = EntityClass.withName(cmdLine.getArgs()[0]);
        String id = cmdLine.getArgs()[1];

        System.out.println(serializer.vertexToJson(manager.getVertex(id, type)));
        return 0;
    }
}
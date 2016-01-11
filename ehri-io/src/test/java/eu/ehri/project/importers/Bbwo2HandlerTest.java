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

package eu.ehri.project.importers;

import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.exceptions.IntegrityError;
import eu.ehri.project.exceptions.ItemNotFound;
import eu.ehri.project.exceptions.PermissionDenied;
import eu.ehri.project.exceptions.ValidationError;
import eu.ehri.project.importers.exceptions.InputParseError;
import eu.ehri.project.importers.managers.SaxImportManager;
import eu.ehri.project.importers.properties.XmlImportProperties;
import eu.ehri.project.models.DocumentaryUnitDescription;
import eu.ehri.project.models.DocumentaryUnit;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.Link;
import eu.ehri.project.models.AccessPoint;
import eu.ehri.project.models.base.Description;
import eu.ehri.project.models.base.Linkable;
import eu.ehri.project.models.base.PermissionScope;
import eu.ehri.project.models.cvoc.Concept;
import eu.ehri.project.models.cvoc.Vocabulary;
import eu.ehri.project.persistence.Bundle;
import eu.ehri.project.views.impl.CrudViews;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Bbwo2HandlerTest extends AbstractImporterTest {
    private static final Logger logger = LoggerFactory.getLogger(Bbwo2HandlerTest.class);
    protected final String TEST_REPO = "r1";
    protected final String XMLFILE_NL = "bbwo2.xml";
    protected final String ARCHDESC = "1505";
    DocumentaryUnit archdesc;
    int origCount = 0;

    @Test
    public void bbwo2Test() throws ItemNotFound, IOException, ValidationError, InputParseError, PermissionDenied, IntegrityError {

        PermissionScope agent = manager.getEntity(TEST_REPO, PermissionScope.class);
        final String logMessage = "Importing an example BBWO2 DC";
        
        //id="joodse-raad" source="niod-trefwoorden" term="Kinderen"
        Bundle vocabularyBundle = new Bundle(EntityClass.CVOC_VOCABULARY)
                                .withDataValue(Ontology.IDENTIFIER_KEY, "niod-trefwoorden")
                                .withDataValue(Ontology.NAME_KEY, "NIOD Keywords");
        Bundle conceptBundle = new Bundle(EntityClass.CVOC_CONCEPT)
                                .withDataValue(Ontology.IDENTIFIER_KEY, "joodse-raad");
        Vocabulary vocabulary = new CrudViews<>(graph, Vocabulary.class).create(vocabularyBundle, validUser);
        Concept concept_716 = new CrudViews<>(graph, Concept.class).create(conceptBundle, validUser);
        vocabulary.addItem(concept_716);
        
        
        Vocabulary vocabularyTest = manager.getEntity("niod_trefwoorden", Vocabulary.class);
        assertNotNull(vocabularyTest);

        
        // Before...
        List<VertexProxy> graphState1 = getGraphState(graph);
        
        
        origCount = getNodeCount(graph);
        InputStream ios = ClassLoader.getSystemResourceAsStream(XMLFILE_NL);
        ImportLog log = new SaxImportManager(graph, agent, validUser, EadImporter.class, DcEuropeanaHandler.class, new XmlImportProperties("dceuropeana.properties")).importFile(ios, logMessage);
//        printGraph(graph);
        // After...
        List<VertexProxy> graphState2 = getGraphState(graph);
        GraphDiff diff = diffGraph(graphState1, graphState2);
        diff.printDebug(System.out);

        /**
         * null: 2
         * relationship: 4
         * DocumentaryUnit: 1
         * link: 1
         * property: 1
         * documentDescription: 1
         * systemEvent: 1
         * 
         */
        int newCount = origCount + 10;
        assertEquals(newCount, getNodeCount(graph));

        DocumentaryUnit archdesc = graph.frame(getVertexByIdentifier(graph, ARCHDESC), DocumentaryUnit.class);
        assertNotNull(archdesc);
        for (DocumentaryUnitDescription d : archdesc.getDocumentDescriptions()) {
            assertEquals("More refugee children arrive from Germany - in time ...", d.getName());
            assertEquals("1505", d.getProperty("sourceFileId"));
            logger.debug("id:"+d.getId() + " - identifier:" + archdesc.getProperty("identifier"));
            //unitDates: [23-12-1938 (Opname), 23-12-1938 (Opname)]
            assertEquals("23-12-1938 (Opname)", d.getProperty("unitDates"));
        }

        for(Concept concept : vocabularyTest.getConcepts()){
            logger.debug("concept:"+concept.getIdentifier());
        }
        
        boolean passTest = false;
        DocumentaryUnit person = manager.getEntity("nl-r1-1505", DocumentaryUnit.class);
        for (Description d : person.getDescriptions()) {
            for (AccessPoint rel : d.getAccessPoints()) {
                if (rel.getRelationshipType().equals("subjectAccess")) {
                    if (rel.getName().equals("kinderen")) {
                        assertEquals(1, toList(rel.getLinks()).size());
                        for (Link link : rel.getLinks()) {
                            boolean conceptFound = false;
                            for (Linkable le : link.getLinkTargets()) {
                                if (le.getType().equals("CvocConcept")) {
                                    assertEquals(le, concept_716);
                                    conceptFound = true;
                                }
                            }
                            assertTrue(conceptFound);
                            passTest=true;
                            logger.debug(link.getLinkType());
                            for (String key : link.getPropertyKeys()) {
                                logger.debug(key + ":" + link.getProperty(key));
                            }
                        }
                    }
                }
            }
        }
        assertTrue(passTest);
        printGraph(graph);
    }
}
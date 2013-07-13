package eu.ehri.project.persistance;

import com.google.common.collect.Maps;
import com.google.common.hash.HashCode;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.base.DescribedEntity;
import eu.ehri.project.models.base.IdentifiableEntity;
import eu.ehri.project.models.base.NamedEntity;
import eu.ehri.project.persistance.utils.BundleUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotSame;
import static junit.framework.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * User: michaelb
 */
public class BundleTest {

    private Bundle bundle;

    @Before
    public void setUp() throws Exception {
        bundle = new Bundle(EntityClass.DOCUMENTARY_UNIT)
                .withDataValue(IdentifiableEntity.IDENTIFIER_KEY, "foobar")
                .withRelation(DescribedEntity.DESCRIBES,
                        new Bundle(EntityClass.DOCUMENT_DESCRIPTION)
                            .withDataValue(NamedEntity.NAME, "Foobar"));
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testCreationWithNullValues() throws Exception {
        Map<String,Object> data = Maps.newHashMap();
        data.put("identifier", null);
        Bundle b2 = new Bundle(EntityClass.DOCUMENTARY_UNIT, data);
        assertSame(data.get("identifier"), b2.getDataValue("identifier"));
    }

    @Test
    public void testGetId() throws Exception {
        assertNull(bundle.getId());
    }

    @Test
    public void testWithId() throws Exception {
        assertEquals("foo", bundle.withId("foo").getId());
    }

    @Test
    public void testGetType() throws Exception {
        assertEquals(EntityClass.DOCUMENTARY_UNIT, bundle.getType());
    }

    @Test
    public void testGetDataValue() throws Exception {
        assertEquals("foobar", bundle.getDataValue(IdentifiableEntity.IDENTIFIER_KEY));
        assertNull(bundle.getDataValue(NamedEntity.NAME));
    }

    @Test
    public void testWithDataValue() throws Exception {
        Bundle b2 = bundle.withDataValue("testkey", "testvalue");
        assertEquals(b2.getDataValue("testkey"), "testvalue");
    }

    @Test
    public void testRemoveDataValue() throws Exception {
        Bundle b2 = bundle.withDataValue("testkey", "testvalue");
        assertEquals(b2.getDataValue("testkey"), "testvalue");
        assertNull(b2.removeDataValue("testkey").getDataValue("testkey"));
    }

    @Test
    public void testGetData() throws Exception {
        Map<String,Object> data = bundle.getData();
        assertEquals("foobar", data.get(IdentifiableEntity.IDENTIFIER_KEY));
    }

    @Test
    public void testWithData() throws Exception {
        HashMap<String,Object> map = Maps.newHashMap();
        map.put("foo", "bar");
        Bundle b2 = bundle.withData(map);
        assertNull(b2.getDataValue(IdentifiableEntity.IDENTIFIER_KEY));
        assertEquals("bar", b2.getDataValue("foo"));
    }

    @Test
    public void testGetRelations() throws Exception {
        // TODO
    }

    @Test
    public void testWithRelations() throws Exception {
        // TODO
    }

    @Test
    public void testWithRelation() throws Exception {
        // TODO
    }

    @Test
    public void testHasRelations() throws Exception {
        // TODO
    }

    @Test
    public void testRemoveRelation() throws Exception {
        // TODO
    }

    @Test
    public void testRemoveRelations() throws Exception {
        // TODO
    }

    @Test
    public void testGetBundleClass() throws Exception {
        // TODO
    }

    @Test
    public void testGetPropertyKeys() throws Exception {
        // TODO
    }

    @Test
    public void testGetUniquePropertyKeys() throws Exception {
        // TODO
    }

    @Test
    public void testFromData() throws Exception {
        // TODO
    }

    @Test
    public void testToData() throws Exception {
        // TODO
    }

    @Test
    public void testFromString() throws Exception {
        // TODO
    }

    @Test
    public void testToJson() throws Exception {
        // TODO
    }

    @Test
    public void testToXml() throws Exception {
        // TODO
    }
}

package eu.ehri.project.models;

import com.google.common.collect.Lists;
import eu.ehri.project.models.base.Description;
import eu.ehri.project.test.AbstractFixtureTest;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
public class VirtualCollectionTest extends AbstractFixtureTest {
    @Test
    public void testGetChildCount() throws Exception {
        VirtualCollection vc1 = manager.getFrame("vc1", VirtualCollection.class);
        assertEquals(Long.valueOf(1L), vc1.getChildCount());
    }

    @Test
    public void testGetParent() throws Exception {
        VirtualCollection vc1 = manager.getFrame("vc1", VirtualCollection.class);
        VirtualCollection vc2 = manager.getFrame("vc2", VirtualCollection.class);
        assertEquals(vc1, vc2.getParent());
    }

    @Test
    public void testAddChild() throws Exception {
        VirtualCollection vc1 = manager.getFrame("vc1", VirtualCollection.class);
        VirtualCollection vc3 = manager.getFrame("vc3", VirtualCollection.class);
        Long childCount = vc1.getChildCount();
        assertTrue(vc1.addChild(vc3));
        assertEquals(Long.valueOf(childCount + 1), vc1.getChildCount());
        // Doing the same thing twice should return false
        assertFalse(vc1.addChild(vc3));
    }

    @Test
    public void testAddChildWithBadChild() throws Exception {
        VirtualCollection vc1 = manager.getFrame("vc1", VirtualCollection.class);
        VirtualCollection vc2 = manager.getFrame("vc1", VirtualCollection.class);
        // This shouldn't be allowed!
        assertFalse(vc1.addChild(vc1));
        // Nor should this - loop
        assertFalse(vc2.addChild(vc1));

    }

    @Test
    public void testGetAncestors() throws Exception {
        VirtualCollection vc1 = manager.getFrame("vc1", VirtualCollection.class);
        VirtualCollection vc2 = manager.getFrame("vc2", VirtualCollection.class);
        assertEquals(Lists.newArrayList(vc1), Lists.newArrayList(vc2.getAncestors()));
    }

    @Test
    public void testGetChildren() throws Exception {
        VirtualCollection vc1 = manager.getFrame("vc1", VirtualCollection.class);
        VirtualCollection vc2 = manager.getFrame("vc2", VirtualCollection.class);
        assertEquals(Lists.newArrayList(vc2), Lists.newArrayList(vc1.getChildren()));
    }

    @Test
    public void testGetAllChildren() throws Exception {
        VirtualCollection vc1 = manager.getFrame("vc1", VirtualCollection.class);
        VirtualCollection vc2 = manager.getFrame("vc2", VirtualCollection.class);
        assertEquals(Lists.newArrayList(vc2), Lists.newArrayList(vc1.getAllChildren()));
    }

    @Test
    public void testGetAuthor() throws Exception {
        VirtualCollection vc1 = manager.getFrame("vc1", VirtualCollection.class);
        UserProfile linda = manager.getFrame("linda", UserProfile.class);
        assertEquals(linda, vc1.getAuthor());
    }

    @Test
    public void testGetDescriptions() throws Exception {
        Description cd1 = manager.getFrame("cd1", Description.class);
        VirtualCollection vc1 = manager.getFrame("vc1", VirtualCollection.class);
        assertEquals(Lists.newArrayList(cd1), Lists.newArrayList(vc1.getDescriptions()));
    }
}
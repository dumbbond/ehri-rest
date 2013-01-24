package eu.ehri.project.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import eu.ehri.project.exceptions.ItemNotFound;
import eu.ehri.project.models.Annotation;
import eu.ehri.project.models.DocumentaryUnit;
import eu.ehri.project.models.UserProfile;
import eu.ehri.project.models.base.AnnotatableEntity;

public class AnnotationTest extends ModelTestBase {

    // NB: These must match up with the JSON fixture...
    public static final String TEST_ANNOTATION_BODY = "Test Annotation";
    public static final String TEST_ANNOTATION_ANNOTATION_BODY = "Test Annotation of Annotation";

    @Test
    public void testUserHasAnnotation() throws ItemNotFound {
        UserProfile mike = manager.getFrame("mike", UserProfile.class);
        assertTrue(mike.getAnnotations().iterator().hasNext());
        assertEquals(mike.getAnnotations().iterator().next().getBody(),
                TEST_ANNOTATION_BODY);
    }

    @Test
    public void testUserHasAnnotationWithTarget() throws ItemNotFound {
        UserProfile mike = manager.getFrame("mike", UserProfile.class);
        DocumentaryUnit c1 = manager.getFrame("c1", DocumentaryUnit.class);
        Annotation annotation = mike.getAnnotations().iterator().next();
        assertEquals(toList(mike.getAnnotations()).get(0), annotation);
        assertEquals(toList(c1.getAnnotations()).get(0), annotation);

        // Check target
        assertEquals(annotation.getTargets().iterator().next().asVertex().getId(), c1.asVertex()
                .getId());
    }

    @Test
    public void testAnnotationAnnotation() throws ItemNotFound {
        AnnotatableEntity ann1 = manager.getFrame("ann1",
                AnnotatableEntity.class);
        Annotation ann2 = manager.getFrame("ann2", Annotation.class);

        assertEquals(ann2.getTargets().iterator().next(), ann1);
        assertEquals(ann1.getAnnotations().iterator().next().getBody(),
                ann2.getBody());
    }
}

package com.vectras.vm.rafaelia.concept;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class ConceptHouseModelTest {

    @Test
    public void testLayerCount() {
        assertEquals(7, ConceptHouseModel.Layer.values().length);
    }

    @Test
    public void testWindowCount() {
        assertEquals(4, ConceptHouseModel.Window.values().length);
    }

    @Test
    public void testSemanticVectorCount() {
        assertEquals(8, ConceptHouseModel.SemanticVector.values().length);
    }

    @Test
    public void testBlendingCount() {
        assertEquals(5, ConceptHouseModel.Blending.values().length);
    }

    @Test
    public void testAddNodeAndRetrieve() {
        ConceptHouseModel model = ConceptHouseModel.create();
        ConceptHouseModel.ConceptNode node = new ConceptHouseModel.ConceptNode(
                "id1", "signal-A", ConceptHouseModel.Layer.L1_SINAIS, 0.8,
                new double[]{0.5, 0.6, 0.7, 0.3, 0.4, 0.5, 0.6, 0.7});
        model.addNode(node);
        assertEquals(1, model.nodesAt(ConceptHouseModel.Layer.L1_SINAIS).size());
        assertEquals(1, model.totalNodes());
    }

    @Test
    public void testGeometricInvariantEmptyModelFails() {
        ConceptHouseModel model = ConceptHouseModel.create();
        assertFalse(model.isGeometricallyCoherent());
    }

    @Test
    public void testGeometricInvariantWithHighCoherenceNodes() {
        ConceptHouseModel model = ConceptHouseModel.create();
        for (ConceptHouseModel.Layer layer : ConceptHouseModel.Layer.values()) {
            double[] weights = new double[8];
            for (int i = 0; i < 8; i++) weights[i] = 0.8;
            model.addNode(new ConceptHouseModel.ConceptNode(
                    layer.name(), layer.name(), layer, 0.9, weights));
        }
        assertTrue(model.isGeometricallyCoherent());
    }

    @Test
    public void testProjectLayerInUnitRange() {
        ConceptHouseModel model = ConceptHouseModel.create();
        double[] w = {0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5};
        model.addNode(new ConceptHouseModel.ConceptNode("n1", "n1",
                ConceptHouseModel.Layer.L4_CONCEITOS, 0.7, w));
        double proj = model.projectLayer(ConceptHouseModel.Layer.L4_CONCEITOS,
                ConceptHouseModel.SemanticVector.SEMANTICO);
        assertTrue(proj >= 0.0 && proj <= 1.0);
    }

    @Test
    public void testBlendingAllStrategiesInUnitRange() {
        ConceptHouseModel model = ConceptHouseModel.create();
        double[] w = {0.6, 0.6, 0.6, 0.6, 0.6, 0.6, 0.6, 0.6};
        for (ConceptHouseModel.Layer l : ConceptHouseModel.Layer.values()) {
            model.addNode(new ConceptHouseModel.ConceptNode(l.name(), l.name(), l, 0.8, w));
        }
        for (ConceptHouseModel.Blending b : ConceptHouseModel.Blending.values()) {
            for (ConceptHouseModel.Window win : ConceptHouseModel.Window.values()) {
                double score = model.blend(b, win);
                assertTrue("blend " + b + "/" + win + " = " + score, score >= 0.0 && score <= 1.0);
            }
        }
    }

    @Test
    public void testLayerDepths() {
        assertEquals(1, ConceptHouseModel.Layer.L1_SINAIS.depth());
        assertEquals(7, ConceptHouseModel.Layer.L7_META_ABSTRATA.depth());
    }

    @Test
    public void testIsAbstractLayers() {
        assertTrue(ConceptHouseModel.Layer.L6_FORMAL.isAbstract());
        assertTrue(ConceptHouseModel.Layer.L7_META_ABSTRATA.isAbstract());
        assertFalse(ConceptHouseModel.Layer.L1_SINAIS.isAbstract());
    }
}

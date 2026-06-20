package fastsoftware3d.terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A compositor that manages a stack of FastTerminalScene layers.
 * It flattens all visible layers down to a single master scene,
 * treating '\0' as transparent pixels.
 */
public class LayeredScene {

    public static class Layer {
        public String name;
        public boolean visible = true;
        public FastTerminalScene scene;

        public Layer(String name, int width, int height) {
            this.name = name;
            this.scene = new FastTerminalScene(0, 0, width, height);
            clear();
        }

        public void clear() {
            scene.clear();
        }
    }

    private List<Layer> layers = new ArrayList<>();
    private int width;
    private int height;

    public LayeredScene(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public List<Layer> getLayers() {
        return Collections.unmodifiableList(layers);
    }

    public Layer addLayer(String name) {
        Layer l = new Layer(name, width, height);
        layers.add(l);
        return l;
    }

    public void removeLayer(Layer layer) {
        layers.remove(layer);
    }

    public void moveLayerUp(Layer layer) {
        int idx = layers.indexOf(layer);
        if (idx < layers.size() - 1) {
            layers.remove(idx);
            layers.add(idx + 1, layer);
        }
    }

    public void moveLayerDown(Layer layer) {
        int idx = layers.indexOf(layer);
        if (idx > 0) {
            layers.remove(idx);
            layers.add(idx - 1, layer);
        }
    }

    /**
     * Composites all visible layers from bottom to top onto the target scene.
     */
    public void flattenTo(FastTerminalScene target) {
        for (Layer l : layers) {
            if (!l.visible) continue;
            
            long[] cells = l.scene.getCells();
            
            for (int r = 0; r < height; r++) {
                for (int c = 0; c < width; c++) {
                    int idx = r * width + c;
                    long cell = cells[idx];
                    int ch = FastTerminalScene.unpackCodepoint(cell);
                    if (ch != '\0') {
                        target.writeCell(c, r, ch, FastTerminalScene.unpackFg(cell), FastTerminalScene.unpackBg(cell));
                    }
                }
            }
        }
    }
}

package patrol;

import arc.files.Fi;
import arc.util.serialization.JsonReader;
import arc.util.serialization.JsonValue;
import mindustry.Vars;

public class CabotNetwork {
    private float[][] w1, w2;
    private float[] b1, b2;
    private int inputSize = 10;
    private int hiddenSize = 24;
    private int outputSize = 8;

    public CabotNetwork() {
        w1 = new float[inputSize][hiddenSize];
        b1 = new float[hiddenSize];
        w2 = new float[hiddenSize][outputSize];
        b2 = new float[outputSize];
        loadWeights();
    }

    private void loadWeights() {
        try {
            Fi file = Vars.mods.getMod("sky-raider").root.child("cabot-weights.json");
            String jsonStr = file.readString();
            JsonValue json = new JsonReader().parse(jsonStr);

            JsonValue w1Json = json.get("w1");
            for (int i = 0; i < inputSize; i++) {
                JsonValue row = w1Json.get(i);
                for (int j = 0; j < hiddenSize; j++) {
                    w1[i][j] = row.getFloat(j);
                }
            }

            JsonValue b1Json = json.get("b1");
            for (int j = 0; j < hiddenSize; j++) {
                b1[j] = b1Json.getFloat(j);
            }

            JsonValue w2Json = json.get("w2");
            for (int j = 0; j < hiddenSize; j++) {
                JsonValue row = w2Json.get(j);
                for (int k = 0; k < outputSize; k++) {
                    w2[j][k] = row.getFloat(k);
                }
            }

            JsonValue b2Json = json.get("b2");
            for (int k = 0; k < outputSize; k++) {
                b2[k] = b2Json.getFloat(k);
            }
        } catch (Exception e) {
            for (int i = 0; i < inputSize; i++)
                for (int j = 0; j < hiddenSize; j++)
                    w1[i][j] = (float)(Math.random() - 0.5) * 0.1f;
            for (int j = 0; j < hiddenSize; j++)
                for (int k = 0; k < outputSize; k++)
                    w2[j][k] = (float)(Math.random() - 0.5) * 0.1f;
        }
    }

    private float relu(float x) {
        return x > 0 ? x : 0;
    }

    public int predict(float[] input) {
        float[] hidden = new float[hiddenSize];
        for (int j = 0; j < hiddenSize; j++) {
            float sum = b1[j];
            for (int i = 0; i < inputSize; i++) {
                sum += input[i] * w1[i][j];
            }
            hidden[j] = relu(sum);
        }

        int bestAction = 0;
        float bestScore = -Float.MAX_VALUE;
        for (int k = 0; k < outputSize; k++) {
            float sum = b2[k];
            for (int j = 0; j < hiddenSize; j++) {
                sum += hidden[j] * w2[j][k];
            }
            if (sum > bestScore) {
                bestScore = sum;
                bestAction = k;
            }
        }
        return bestAction;
    }
}
package com.bluelock3d;

import com.jme3.app.SimpleApplication;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.RenderManager;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.jme3.material.Material;

/**
 * Blue Lock 3D - Main application entry point.
 * Third-person football game inspired by Blue Lock.
 */
public class Main extends SimpleApplication {

    private Player player;
    private Ball ball;
    private Goalkeeper goalkeeper;
    private Defender[] defenders;
    private GameManager gameManager;
    private GameUI gameUI;

    // Pitch dimensions (half-sizes)
    public static final float PITCH_HALF_LENGTH = 50f; // Z
    public static final float PITCH_HALF_WIDTH  = 30f; // X
    public static final float GOAL_WIDTH       = 7.32f;
    public static final float GOAL_HEIGHT      = 2.44f;
    public static final float GOAL_DEPTH       = 2f;

    // Input mapping names
    private static final String MOVE_FORWARD  = "MoveForward";
    private static final String MOVE_BACK     = "MoveBack";
    private static final String MOVE_LEFT     = "MoveLeft";
    private static final String MOVE_RIGHT    = "MoveRight";
    private static final String SPRINT        = "Sprint";
    private static final String JUMP          = "Jump";
    private static final String SHOOT         = "Shoot";
    private static final String DIRECT_SHOT   = "DirectShot";

    private boolean shootingHeld = false;
    private float shotCharge = 0f;
    private static final float MAX_CHARGE_TIME = 1.2f;

    public static void main(String[] args) {
        Main app = new Main();
        app.setShowSettings(false);
        app.start();
    }

    @Override
    public void simpleInitApp() {
        flyCam.setEnabled(false);
        setDisplayStatView(false);
        setDisplayFps(true);

        // Lighting
        AmbientLight ambient = new AmbientLight();
        ambient.setColor(ColorRGBA.White.mult(0.4f));
        rootNode.addLight(ambient);

        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-0.5f, -1f, -0.3f).normalizeLocal());
        sun.setColor(ColorRGBA.White.mult(0.9f));
        rootNode.addLight(sun);

        createPitch();
        createGoals();

        // Ball
        ball = new Ball(assetManager, rootNode);
        ball.reset(new Vector3f(0, 0.5f, 0));

        // Player (blue)
        player = new Player(assetManager, rootNode, new Vector3f(0, 0, -20f));
        player.setColor(new ColorRGBA(0.1f, 0.3f, 0.9f, 1f));

        // Goalkeeper (red)
        goalkeeper = new Goalkeeper(assetManager, rootNode,
                new Vector3f(0, 0, PITCH_HALF_LENGTH - 2f));
        goalkeeper.setColor(new ColorRGBA(0.9f, 0.15f, 0.15f, 1f));

        // Defenders (red)
        defenders = new Defender[3];
        defenders[0] = new Defender(assetManager, rootNode, new Vector3f(-8f, 0, 15f));
        defenders[1] = new Defender(assetManager, rootNode, new Vector3f( 8f, 0, 15f));
        defenders[2] = new Defender(assetManager, rootNode, new Vector3f( 0f, 0, 25f));
        for (Defender d : defenders) {
            d.setColor(new ColorRGBA(0.85f, 0.2f, 0.2f, 1f));
        }

        gameManager = new GameManager(player, ball, goalkeeper, defenders);
        gameUI = new GameUI(guiNode, guiFont, settings.getWidth(), settings.getHeight());

        setupInput();

        cam.setLocation(player.getPosition().add(0, 8f, -12f));
        cam.lookAt(player.getPosition().add(0, 1f, 5f), Vector3f.UNIT_Y);
    }

    private void createPitch() {
        Box pitchBox = new Box(PITCH_HALF_WIDTH, 0.1f, PITCH_HALF_LENGTH);
        Geometry pitchGeo = new Geometry("Pitch", pitchBox);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", new ColorRGBA(0.15f, 0.55f, 0.2f, 1f));
        pitchGeo.setMaterial(mat);
        pitchGeo.setLocalTranslation(0, -0.1f, 0);
        rootNode.attachChild(pitchGeo);

        Material lineMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        lineMat.setColor("Color", ColorRGBA.White);

        // Center line
        Box centerLine = new Box(PITCH_HALF_WIDTH, 0.05f, 0.15f);
        Geometry cl = new Geometry("CenterLine", centerLine);
        cl.setMaterial(lineMat);
        cl.setLocalTranslation(0, 0.01f, 0);
        rootNode.attachChild(cl);

        // Center circle
        Cylinder circle = new Cylinder(32, 32, 9.15f, 0.08f, true);
        Geometry circleGeo = new Geometry("CenterCircle", circle);
        circleGeo.setMaterial(lineMat);
        circleGeo.rotate((float) Math.PI / 2, 0, 0);
        circleGeo.setLocalTranslation(0, 0.02f, 0);
        rootNode.attachChild(circleGeo);

        // Touch lines
        Box leftTouch = new Box(0.15f, 0.05f, PITCH_HALF_LENGTH);
        Geometry lt = new Geometry("LeftTouch", leftTouch);
        lt.setMaterial(lineMat);
        lt.setLocalTranslation(-PITCH_HALF_WIDTH, 0.01f, 0);
        rootNode.attachChild(lt);

        Geometry rt = new Geometry("RightTouch", leftTouch);
        rt.setMaterial(lineMat);
        rt.setLocalTranslation(PITCH_HALF_WIDTH, 0.01f, 0);
        rootNode.attachChild(rt);

        // Goal lines
        Box goalLine = new Box(PITCH_HALF_WIDTH, 0.05f, 0.15f);
        Geometry gl1 = new Geometry("GoalLine1", goalLine);
        gl1.setMaterial(lineMat);
        gl1.setLocalTranslation(0, 0.01f, -PITCH_HALF_LENGTH);
        rootNode.attachChild(gl1);

        Geometry gl2 = new Geometry("GoalLine2", goalLine);
        gl2.setMaterial(lineMat);
        gl2.setLocalTranslation(0, 0.01f, PITCH_HALF_LENGTH);
        rootNode.attachChild(gl2);
    }

    private void createGoals() {
        Material postMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        postMat.setColor("Color", ColorRGBA.White);
        createGoalFrame(0, PITCH_HALF_LENGTH, postMat);
        createGoalFrame(0, -PITCH_HALF_LENGTH, postMat);
    }

    private void createGoalFrame(float x, float z, Material mat) {
        float halfW = GOAL_WIDTH / 2f;

        Box post = new Box(0.1f, GOAL_HEIGHT / 2f, 0.1f);
        Geometry left = new Geometry("LeftPost", post);
        left.setMaterial(mat);
        left.setLocalTranslation(x - halfW, GOAL_HEIGHT / 2f, z);
        rootNode.attachChild(left);

        Geometry right = new Geometry("RightPost", post);
        right.setMaterial(mat);
        right.setLocalTranslation(x + halfW, GOAL_HEIGHT / 2f, z);
        rootNode.attachChild(right);

        Box bar = new Box(halfW, 0.1f, 0.1f);
        Geometry cross = new Geometry("Crossbar", bar);
        cross.setMaterial(mat);
        cross.setLocalTranslation(x, GOAL_HEIGHT, z);
        rootNode.attachChild(cross);

        Material netMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        netMat.setColor("Color", new ColorRGBA(1f, 1f, 1f, 0.3f));
        netMat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        Box net = new Box(halfW, GOAL_HEIGHT / 2f, GOAL_DEPTH / 2f);
        Geometry netGeo = new Geometry("Net", net);
        netGeo.setMaterial(netMat);
        netGeo.setLocalTranslation(x, GOAL_HEIGHT / 2f, z + (z > 0 ? GOAL_DEPTH / 2f : -GOAL_DEPTH / 2f));
        netGeo.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Transparent);
        rootNode.attachChild(netGeo);
    }

    private void setupInput() {
        inputManager.addMapping(MOVE_FORWARD,  new KeyTrigger(KeyInput.KEY_W));
        inputManager.addMapping(MOVE_BACK,     new KeyTrigger(KeyInput.KEY_S));
        inputManager.addMapping(MOVE_LEFT,     new KeyTrigger(KeyInput.KEY_A));
        inputManager.addMapping(MOVE_RIGHT,    new KeyTrigger(KeyInput.KEY_D));
        inputManager.addMapping(SPRINT,        new KeyTrigger(KeyInput.KEY_LSHIFT));
        inputManager.addMapping(JUMP,          new KeyTrigger(KeyInput.KEY_SPACE));
        inputManager.addMapping(SHOOT,         new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping(DIRECT_SHOT,   new KeyTrigger(KeyInput.KEY_E));

        inputManager.addListener(actionListener, MOVE_FORWARD, MOVE_BACK, MOVE_LEFT, MOVE_RIGHT,
                SPRINT, JUMP, SHOOT, DIRECT_SHOT);
        inputManager.addListener(analogListener, MOVE_FORWARD, MOVE_BACK, MOVE_LEFT, MOVE_RIGHT);
    }

    private final ActionListener actionListener = new ActionListener() {
        @Override
        public void onAction(String name, boolean isPressed, float tpf) {
            switch (name) {
                case MOVE_FORWARD:
                    player.setMovingForward(isPressed);
                    break;
                case MOVE_BACK:
                    player.setMovingBack(isPressed);
                    break;
                case MOVE_LEFT:
                    player.setMovingLeft(isPressed);
                    // Press A while moving → sharp dribble to the RIGHT
                    if (isPressed) player.tryDribble(false);
                    break;
                case MOVE_RIGHT:
                    player.setMovingRight(isPressed);
                    // Press D while moving → sharp dribble to the LEFT
                    if (isPressed) player.tryDribble(true);
                    break;
                case SPRINT:
                    player.setSprinting(isPressed);
                    break;
                case JUMP:
                    if (isPressed) player.jump();
                    break;
                case SHOOT:
                    if (isPressed) {
                        shootingHeld = true;
                        shotCharge = 0f;
                    } else {
                        if (shootingHeld && gameManager.playerHasBall()) {
                            float power = Math.min(shotCharge / MAX_CHARGE_TIME, 1f);
                            player.shoot(ball, power, cam.getDirection());
                            gameManager.onPlayerShot();
                            gameUI.showShotEffect();
                        }
                        shootingHeld = false;
                        shotCharge = 0f;
                    }
                    break;
                case DIRECT_SHOT:
                    if (isPressed) {
                        if (gameManager.canDirectShot()) {
                            player.directShot(ball, cam.getDirection());
                            gameManager.onDirectShotUsed();
                            gameUI.showDirectShotText();
                        }
                    }
                    break;
            }
        }
    };

    private final AnalogListener analogListener = new AnalogListener() {
        @Override
        public void onAnalog(String name, float value, float tpf) {
            // Continuous movement handled via flags in Player.update
        }
    };

    @Override
    public void simpleUpdate(float tpf) {
        if (shootingHeld && gameManager.playerHasBall()) {
            shotCharge += tpf;
            if (shotCharge > MAX_CHARGE_TIME) shotCharge = MAX_CHARGE_TIME;
        }

        player.update(tpf, ball, gameManager);
        ball.update(tpf);
        goalkeeper.update(tpf, ball, gameManager);
        for (Defender d : defenders) {
            d.update(tpf, player, ball, gameManager);
        }

        gameManager.update(tpf);

        // Third-person camera
        Vector3f playerPos = player.getPosition();
        Vector3f camOffset = new Vector3f(0, 9f, -14f);
        Vector3f targetCam = playerPos.add(camOffset);
        cam.setLocation(cam.getLocation().interpolateLocal(targetCam, 4f * tpf));
        Vector3f lookTarget = playerPos.add(0, 1.2f, 6f);
        lookTarget = lookTarget.add(ball.getPosition().subtract(playerPos).mult(0.25f));
        cam.lookAt(lookTarget, Vector3f.UNIT_Y);

        float chargePct = shootingHeld ? (shotCharge / MAX_CHARGE_TIME) : 0f;
        gameUI.update(gameManager, chargePct, player.hasBall(), tpf);
    }

    @Override
    public void simpleRender(RenderManager rm) {
    }
}
package com.bluelock3d;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Capsule;
import com.jme3.scene.shape.Sphere;

/**
 * Controllable striker. Handles movement, dribbling, shooting and Direct Shot.
 */
public class Player {

    private final Node root;
    private final Geometry body;
    private final Geometry head;

    private Vector3f position = new Vector3f();
    private Vector3f velocity = new Vector3f();
    private float facingAngle = 0f;

    private boolean movingForward, movingBack, movingLeft, movingRight;
    private boolean sprinting;
    private boolean onGround = true;

    private float dribbleCooldown = 0f;
    private static final float DRIBBLE_COOLDOWN = 0.55f;
    private static final float DRIBBLE_FORCE = 18f;
    private boolean dribbleLeftQueued = false;
    private boolean dribbleRightQueued = false;

    private boolean hasBall = false;
    private float possessionRange = 1.6f;

    private float verticalVel = 0f;
    private static final float JUMP_SPEED = 8f;
    private static final float GRAVITY = 22f;

    private static final float WALK_SPEED = 9f;
    private static final float SPRINT_SPEED = 14f;

    public Player(AssetManager assetManager, Node parent, Vector3f startPos) {
        this.root = new Node("Player");
        this.position.set(startPos);

        Capsule bodyShape = new Capsule(8, 8, 0.35f, 1.0f);
        body = new Geometry("Body", bodyShape);
        Material bodyMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        bodyMat.setColor("Color", new ColorRGBA(0.1f, 0.35f, 0.95f, 1f));
        body.setMaterial(bodyMat);
        body.setLocalTranslation(0, 1.0f, 0);
        root.attachChild(body);

        Sphere headShape = new Sphere(12, 12, 0.28f);
        head = new Geometry("Head", headShape);
        Material headMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        headMat.setColor("Color", new ColorRGBA(0.95f, 0.8f, 0.65f, 1f));
        head.setMaterial(headMat);
        head.setLocalTranslation(0, 1.85f, 0);
        root.attachChild(head);

        parent.attachChild(root);
        root.setLocalTranslation(position);
    }

    public void setColor(ColorRGBA color) {
        body.getMaterial().setColor("Color", color);
    }

    public void setMovingForward(boolean v) { movingForward = v; }
    public void setMovingBack(boolean v)    { movingBack = v; }
    public void setMovingLeft(boolean v)    { movingLeft = v; }
    public void setMovingRight(boolean v)   { movingRight = v; }
    public void setSprinting(boolean v)     { sprinting = v; }

    public void tryDribble(boolean left) {
        if (dribbleCooldown > 0f || !hasBall) return;
        if (left) dribbleLeftQueued = true;
        else dribbleRightQueued = true;
    }

    public void jump() {
        if (onGround) {
            verticalVel = JUMP_SPEED;
            onGround = false;
        }
    }

    public void update(float tpf, Ball ball, GameManager gm) {
        if (dribbleCooldown > 0f) dribbleCooldown -= tpf;

        Vector3f wishDir = new Vector3f();
        if (movingForward) wishDir.z += 1;
        if (movingBack)    wishDir.z -= 1;
        if (movingLeft)    wishDir.x -= 1;
        if (movingRight)   wishDir.x += 1;

        if (wishDir.lengthSquared() > 0.01f) {
            wishDir.normalizeLocal();
            float speed = sprinting ? SPRINT_SPEED : WALK_SPEED;
            facingAngle = FastMath.atan2(wishDir.x, wishDir.z);
            velocity.x = wishDir.x * speed;
            velocity.z = wishDir.z * speed;
        } else {
            velocity.x *= 0.85f;
            velocity.z *= 0.85f;
            if (Math.abs(velocity.x) < 0.1f) velocity.x = 0;
            if (Math.abs(velocity.z) < 0.1f) velocity.z = 0;
        }

        // Dribble burst (sideways cut)
        if (hasBall && (dribbleLeftQueued || dribbleRightQueued) && dribbleCooldown <= 0f) {
            float side = dribbleLeftQueued ? -1f : 1f;
            float cos = FastMath.cos(facingAngle);
            float sin = FastMath.sin(facingAngle);
            Vector3f sideVec = new Vector3f(cos, 0, -sin).multLocal(side * DRIBBLE_FORCE);
            velocity.x += sideVec.x;
            velocity.z += sideVec.z;
            velocity.x += sin * 6f;
            velocity.z += cos * 6f;
            dribbleCooldown = DRIBBLE_COOLDOWN;
            dribbleLeftQueued = false;
            dribbleRightQueued = false;
        }

        // Gravity / jump
        if (!onGround) {
            verticalVel -= GRAVITY * tpf;
            position.y += verticalVel * tpf;
            if (position.y <= 0f) {
                position.y = 0f;
                verticalVel = 0f;
                onGround = true;
            }
        }

        position.x += velocity.x * tpf;
        position.z += velocity.z * tpf;

        position.x = FastMath.clamp(position.x, -Main.PITCH_HALF_WIDTH + 1f, Main.PITCH_HALF_WIDTH - 1f);
        position.z = FastMath.clamp(position.z, -Main.PITCH_HALF_LENGTH + 1f, Main.PITCH_HALF_LENGTH - 1f);

        root.setLocalTranslation(position);
        root.setLocalRotation(new Quaternion().fromAngleAxis(facingAngle, Vector3f.UNIT_Y));

        // Ball possession
        float distToBall = position.distance(ball.getPosition());
        if (distToBall < possessionRange && !ball.isInFlightStrongly()) {
            hasBall = true;
            Vector3f offset = new Vector3f(
                    FastMath.sin(facingAngle) * 0.9f,
                    0.35f,
                    FastMath.cos(facingAngle) * 0.9f
            );
            ball.setPosition(position.add(offset));
            ball.setVelocity(velocity.mult(0.9f));
        } else if (distToBall > possessionRange * 1.4f) {
            hasBall = false;
        }
    }

    public void shoot(Ball ball, float power, Vector3f aimDir) {
        if (!hasBall) return;
        hasBall = false;
        Vector3f dir = aimDir.clone();
        dir.y = FastMath.clamp(dir.y + 0.15f, 0.05f, 0.55f);
        dir.normalizeLocal();
        float speed = 18f + power * 22f;
        ball.kick(dir.mult(speed));
    }

    public void directShot(Ball ball, Vector3f aimDir) {
        float dist = position.distance(ball.getPosition());
        if (dist > 3.5f && !hasBall) return;

        hasBall = false;
        Vector3f dir = aimDir.clone();
        dir.y = FastMath.clamp(dir.y + 0.08f, 0.02f, 0.4f);
        dir.normalizeLocal();
        float speed = 42f;
        ball.kick(dir.mult(speed));
        ball.setDirectShotVisual(true);
    }

    public Vector3f getPosition() { return position; }
    public boolean hasBall() { return hasBall; }
    public void setHasBall(boolean v) { hasBall = v; }
    public float getFacingAngle() { return facingAngle; }
    public Vector3f getVelocity() { return velocity; }
}
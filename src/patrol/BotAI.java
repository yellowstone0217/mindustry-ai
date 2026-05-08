/*
 * Copyright (C) 2025 yellowstone0217
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package patrol;

import arc.math.geom.Vec2;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.gen.Unit;
import mindustry.game.Team;
import mindustry.entities.units.AIController;
import mindustry.type.UnitType;

public class BotAI extends AIController {
    static final float SENSE_RANGE = 300f;
    static final float PATROL_RANGE = 80f;
    
    NeuralNetwork brain = new NeuralNetwork();
    Vec2 home = new Vec2();
    Vec2 target = new Vec2();
    Unit enemy;
    float[] state = new float[10];
    float[] xMax;
    
    int currentAction = 2;
    
    float changeTimer = 0f;
    
    public BotAI(float hx, float hy) {
        home.set(hx, hy);
        target.set(hx, hy);
        initNormalization();
    }
    
    void initNormalization() {
        xMax = new float[]{1.0f, 10f, 500f, 1.0f, 10f, 1.0f, 1f, 50f, 20f, 1.0f};
    }
    
    @Override
    public void updateUnit() {
        if (unit == null || unit.dead()) return;
    
        // 被玩家操控 → 等释放后强制恢复
        if (unit.isPlayer() || unit.getPlayer() != null) {
            unit.controller(this);
            return;
        }
    
        sense();
        currentAction = brain.predict(normalize(state));
        execute(currentAction);
    }
    
    void sense() {
        float coreHP = getCoreHP();
        enemy = findEnemy();
        float enemyCount = countEnemies();
        float nearestDist = enemy != null ? unit.dst(enemy) : SENSE_RANGE;
        float nearestHP = enemy != null ? enemy.health / Math.max(enemy.maxHealth, 1) : 0;
        float allyCount = countAllies();
        float myHP = unit.health / Math.max(unit.maxHealth, 1);
        float underAttack = (enemy != null && nearestDist < 100f && unit.health < unit.maxHealth) ? 1f : 0f;
        float wave = Vars.state.wave;
        float blueprints = countBlueprints();
        float coreThreat = 1f - coreHP;
        
        state[0] = coreHP;
        state[1] = enemyCount;
        state[2] = nearestDist;
        state[3] = nearestHP;
        state[4] = allyCount;
        state[5] = myHP;
        state[6] = underAttack;
        state[7] = wave;
        state[8] = blueprints;
        state[9] = coreThreat;
    }
    
    float[] normalize(float[] raw) {
        float[] norm = new float[10];
        for (int i = 0; i < 10; i++) {
            norm[i] = Math.min(raw[i] / xMax[i], 1f);
        }
        return norm;
    }
    
    void execute(int action) {
        if (action == 0) { doAttack(); }
        else if (action == 1) { doDefend(); }
        else if (action == 2) { doPatrol(); }
        else if (action == 3) { doBuild(); }
        else if (action == 4) { doRetreat(); }
    }
    
    void doAttack() {
        if (enemy != null) {
            float dx = enemy.x - unit.x;
            float dy = enemy.y - unit.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist > 0) {
                float speed = unit.speed() * 0.6f;
                unit.vel.x = (dx / dist) * speed;
                unit.vel.y = (dy / dist) * speed;
            }
            unit.lookAt(enemy);
            unit.aim(enemy);
            unit.controlWeapons(true, true);
        } else {
            doPatrol();
        }
    }
    
    void doDefend() {
        var core = unit.closestCore();
        if (core != null) {
            float dx = core.x - unit.x;
            float dy = core.y - unit.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist > 50f && dist > 0) {
                float speed = unit.speed() * 0.4f;
                unit.vel.x = (dx / dist) * speed;
                unit.vel.y = (dy / dist) * speed;
            } else if (dist > 0) {
                circle(core, 40f);
            }
        }
        if (enemy != null && unit.closestCore() != null && enemy.dst(unit.closestCore()) < 150f) {
            unit.lookAt(enemy);
            unit.aim(enemy);
            unit.controlWeapons(true, true);
        } else {
            unit.controlWeapons(false, false);
        }
    }
    
    void doPatrol() {
        changeTimer += 1f;
        float dx = target.x - unit.x;
        float dy = target.y - unit.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        
        if (dist < 40f || changeTimer > 60f) {
            pickPatrolTarget();
            changeTimer = 0f;
        }
        
        if (dist > 0) {
            float speed = unit.speed() * 0.25f;
            unit.vel.x = (dx / dist) * speed;
            unit.vel.y = (dy / dist) * speed;
            unit.rotation = (float) Math.toDegrees(Math.atan2(dy, dx));
        }
        unit.controlWeapons(false, false);
    }
    
    void pickPatrolTarget() {
        float angle = (float)(Math.random() * Math.PI * 2);
        float dist = 30f + (float)(Math.random() * 50f);
        target.set(
            home.x + (float) Math.cos(angle) * dist,
            home.y + (float) Math.sin(angle) * dist
        );
    }
    
    void doBuild() {
        var plans = unit.team.data().plans;
        if (plans.size > 0) {
            var plan = plans.first();
            float dx = plan.x * 8 - unit.x;
            float dy = plan.y * 8 - unit.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist > 0) {
                float speed = unit.speed() * 0.3f;
                unit.vel.x = (dx / dist) * speed;
                unit.vel.y = (dy / dist) * speed;
            }
        } else {
            doPatrol();
        }
        unit.controlWeapons(false, false);
    }
    
    void doRetreat() {
        var core = unit.closestCore();
        if (core != null) {
            float dx = core.x - unit.x;
            float dy = core.y - unit.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist > 0) {
                float speed = unit.speed() * 0.8f;
                unit.vel.x = (dx / dist) * speed;
                unit.vel.y = (dy / dist) * speed;
                unit.rotation = (float) Math.toDegrees(Math.atan2(dy, dx));
            }
        }
        unit.controlWeapons(false, false);
    }
    
    float getCoreHP() {
        var core = unit.closestCore();
        if (core == null) return 0;
        return core.health / Math.max(core.maxHealth, 1);
    }
    
    Unit findEnemy() {
        Unit closest = null;
        float closestDist = SENSE_RANGE;
        for (Team team : Team.all) {
            if (team == unit.team) continue;
            for (Unit u : team.data().units) {
                if (u == null || u.dead() || !u.type.targetable) continue;
                float dist = unit.dst(u);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = u;
                }
            }
        }
        return closest;
    }
    
    float countEnemies() {
        float count = 0;
        for (Team team : Team.all) {
            if (team == unit.team) continue;
            for (Unit u : team.data().units) {
                if (u != null && !u.dead() && u.type.targetable && unit.dst(u) < SENSE_RANGE) {
                    count++;
                }
            }
        }
        return count;
    }
    
    float countAllies() {
        float count = 0;
        for (Unit u : unit.team.data().units) {
            if (u != null && !u.dead() && u != unit && unit.dst(u) < SENSE_RANGE) {
                count++;
            }
        }
        return count;
    }
    
    float countBlueprints() {
        float count = 0;
        for (var plan : unit.team.data().plans) {
            float dx = plan.x * 8 - unit.x;
            float dy = plan.y * 8 - unit.y;
            if (dx * dx + dy * dy < 100f * 100f) count++;
        }
        return Math.min(count, 20);
    }
}
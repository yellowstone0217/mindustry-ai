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
import mindustry.Vars;
import mindustry.gen.Unit;
import mindustry.game.Team;
import mindustry.entities.units.AIController;

public class BcrAI extends AIController {
    static final float ENGAGE_RANGE = 1200f;
    static final float PATROL_RANGE = 100f;
    
    Vec2 home = new Vec2();
    Vec2 target = new Vec2();
    Unit enemy;
    
    float patrolTimer = 0f;
    Vec2 patrolPoint = new Vec2();
    
    public BcrAI(float hx, float hy) {
        home.set(hx, hy);
        target.set(hx, hy);
        patrolPoint.set(hx, hy);
    }
    
    @Override
    public void updateUnit() {
        if (unit == null || unit.dead()) return;
        if (unit.getPlayer() != null || unit.isPlayer()) return;
        
        // 找500格内最近的敌人
        enemy = findEnemy();
        
        if (enemy != null) {
            attack();
        } else {
            patrol();
        }
        
        moveToTarget();
    }
    
    void attack() {
        target.set(enemy.x, enemy.y);
        float dist = unit.dst(enemy);
        
        if (dist < unit.range() * 0.7f) {
            // 太近了，保持距离
            float dx = enemy.x - unit.x;
            float dy = enemy.y - unit.y;
            if (dist > 0) {
                target.set(
                    unit.x - (dx / dist) * 80f,
                    unit.y - (dy / dist) * 80f
                );
            }
        }
        
        unit.lookAt(enemy);
        unit.aim(enemy);
        unit.controlWeapons(true, true);
    }
    
    void patrol() {
        patrolTimer++;
        if (patrolTimer > 120f || unit.dst(patrolPoint) < 30f) {
            patrolTimer = 0f;
            float angle = (float)(Math.random() * Math.PI * 2);
            float dist = 40f + (float)(Math.random() * PATROL_RANGE);
            patrolPoint.set(
                home.x + (float)Math.cos(angle) * dist,
                home.y + (float)Math.sin(angle) * dist
            );
        }
        target.set(patrolPoint);
        unit.controlWeapons(false, false);
    }
    
    Unit findEnemy() {
        Unit closest = null;
        float closestDist = ENGAGE_RANGE;
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
    
    void moveToTarget() {
        float dx = target.x - unit.x;
        float dy = target.y - unit.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist < 20f || dist <= 0) return;
        
        float speed = unit.speed() * 0.6f;
        unit.vel.x = (dx / dist) * speed;
        unit.vel.y = (dy / dist) * speed;
        unit.rotation = (float) Math.toDegrees(Math.atan2(dy, dx));
    }
}
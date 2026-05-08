package patrol;

import arc.math.geom.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.entities.units.AIController;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.storage.CoreBlock.*;

public class SupplyAI extends AIController {
    Vec2 home = new Vec2();
    Vec2 target = new Vec2();
    
    Item targetItem;
    Tile ore;
    boolean mining = true;
    
    Interval timer = new Interval(5);
    Vec2 patrolPoint = new Vec2();
    float idleTimer = 0f;
    
    public SupplyAI(float hx, float hy) {
        home.set(hx, hy);
        target.set(hx, hy);
        patrolPoint.set(hx, hy);
    }
    
    @Override
    public void updateUnit() {
        if (unit == null || unit.dead()) return;
        
        if (unit.isPlayer() || unit.getPlayer() != null) {
            unit.controller(this);
            return;
        }
        
        CoreBuild core = unit.closestCore();
        if (core == null) return;
        
        if (unit.stack.amount >= unit.type.itemCapacity) {
            deliverToCore(core);
            return;
        }
        
        if (unit.canMine()) {
            if (unit.mineTile != null && !unit.within(unit.mineTile, unit.type.mineRange)) {
                unit.mineTile(null);
            }
            
            if (mining) {
                if (timer.get(0, 240f) || targetItem == null) {
                    targetItem = getMostNeededItem(core);
                }
                
                if (targetItem != null && unit.canMine(targetItem)) {
                    if (timer.get(1, 60f)) {
                        ore = findNearestOre(targetItem);
                    }
                    
                    if (ore != null) {
                        target.set(ore.worldx(), ore.worldy());
                        moveToTarget();
                        
                        if (unit.within(ore, unit.type.mineRange)) {
                            unit.mineTile = ore;
                        }
                    } else {
                        patrol(core);
                    }
                }
            }
        } else {
            patrol(core);
        }
    }
    
    void deliverToCore(CoreBuild core) {
        unit.mineTile = null;
        target.set(core.x, core.y);
        moveToTarget();
        
        if (unit.within(core, unit.type.range)) {
            if (unit.stack.amount > 0) {
                Call.transferItemTo(unit, unit.stack.item, unit.stack.amount, 
                    unit.x, unit.y, core);
            }
            unit.clearItem();
        }
    }
    
    Item getMostNeededItem(CoreBuild core) {
        int thoriumAmount = core.items.get(Items.thorium);
        int titaniumAmount = core.items.get(Items.titanium);
        
        if (thoriumAmount <= titaniumAmount) {
            return Items.thorium;
        } else {
            return Items.titanium;
        }
    }
    
    Tile findNearestOre(Item item) {
        Tile nearest = null;
        float nearestDist = Float.MAX_VALUE;
        
        for (int x = 0; x < Vars.world.width(); x++) {
            for (int y = 0; y < Vars.world.height(); y++) {
                Tile tile = Vars.world.tile(x, y);
                if (tile == null) continue;
                
                Item drop = tile.drop();
                if (drop == null) continue;
                
                if ((drop == Items.thorium || drop == Items.titanium) && 
                    unit.canMine(drop) && drop == item) {
                    float dist = unit.dst(tile.worldx(), tile.worldy());
                    if (dist < 500f && dist < nearestDist) {
                        nearestDist = dist;
                        nearest = tile;
                    }
                }
            }
        }
        
        if (nearest == null) {
            for (int x = 0; x < Vars.world.width(); x++) {
                for (int y = 0; y < Vars.world.height(); y++) {
                    Tile tile = Vars.world.tile(x, y);
                    if (tile == null) continue;
                    
                    Item drop = tile.drop();
                    if (drop == null) continue;
                    
                    if ((drop == Items.thorium || drop == Items.titanium) && 
                        unit.canMine(drop)) {
                        float dist = unit.dst(tile.worldx(), tile.worldy());
                        if (dist < 500f && dist < nearestDist) {
                            nearestDist = dist;
                            nearest = tile;
                        }
                    }
                }
            }
        }
        
        return nearest;
    }
    
    void patrol(CoreBuild core) {
        idleTimer++;
        if (idleTimer > 120f || unit.dst(patrolPoint) < 30f) {
            idleTimer = 0f;
            float angle = (float)(Math.random() * 360f);
            float dist = 30f + (float)(Math.random() * 50f);
            patrolPoint.set(
                core.x + (float)Math.cos(Math.toRadians(angle)) * dist,
                core.y + (float)Math.sin(Math.toRadians(angle)) * dist
            );
        }
        target.set(patrolPoint);
        unit.controlWeapons(false, false);
    }
    
    void moveToTarget() {
        float dx = target.x - unit.x;
        float dy = target.y - unit.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist < 15f || dist <= 0) return;
        
        float speed = unit.speed() * 2f;
        unit.vel.x = (dx / dist) * speed;
        unit.vel.y = (dy / dist) * speed;
        unit.rotation = (float) Math.toDegrees(Math.atan2(dy, dx));
    }
}
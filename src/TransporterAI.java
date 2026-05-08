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

public class TransporterAI extends AIController {
    Vec2 home = new Vec2();
    Vec2 target = new Vec2();
    
    float cooldown = 0f;
    static final float COOLDOWN_TIME = 60f;
    static final int MIN_DELIVER = 30;
    
    Interval timer = new Interval(4);
    Vec2 patrolPoint = new Vec2();
    float idleTimer = 0f;
    
    public TransporterAI(float hx, float hy) {
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
        
        if (cooldown > 0) {
            cooldown -= 60f;
        }
        
        CoreBuild core = unit.closestCore();
        if (core == null) return;
        
        // 身上有物品就送核心
        if (unit.stack.amount >= MIN_DELIVER) {
            deliverToCore(core);
            return;
        }
        
        // 身上有物品但不是同一种，也送核心
        if (unit.stack.amount > 0) {
            deliverToCore(core);
            return;
        }
        
        // 从容器搬运到身上
        if (cooldown <= 0) {
            Tile container = findNearestContainerWithItems();
            if (container != null && container.build != null) {
                target.set(container.worldx(), container.worldy());
                moveToTarget();
                
                if (unit.within(container, 30f)) {
                    // 只拿数量最多的那种物品
                    Item bestItem = null;
                    int bestAmount = 0;
                    
                    for (int i = 0; i < Vars.content.items().size; i++) {
                        Item item = Vars.content.items().get(i);
                        if (container.build.items.has(item)) {
                            int count = container.build.items.get(item);
                            if (count > bestAmount) {
                                bestAmount = count;
                                bestItem = item;
                            }
                        }
                    }
                    
                    if (bestItem != null && unit.acceptsItem(bestItem)) {
                        int amount = Math.min(bestAmount, unit.type.itemCapacity);
                        amount = Math.min(amount, MIN_DELIVER);
                        
                        if (amount > 0) {
                            container.build.items.remove(bestItem, amount);
                            unit.addItem(bestItem, amount);
                            cooldown = COOLDOWN_TIME;
                            return;
                        }
                    }
                }
                return;
            }
        }
        
        // 空闲巡逻
        patrol(core);
    }
    
    Tile findNearestContainerWithItems() {
        Tile nearest = null;
        float nearestDist = 600f;
        
        for (int x = 0; x < Vars.world.width(); x++) {
            for (int y = 0; y < Vars.world.height(); y++) {
                Tile tile = Vars.world.tile(x, y);
                if (tile == null || tile.build == null) continue;
                if (tile.build.team != unit.team) continue;
                if (tile.build.block == null || tile.build.block.name == null) continue;
                
                String name = tile.build.block.name.toLowerCase();
                if (name.contains("container") || name.contains("vault") || name.contains("warehouse")) {
                    if (tile.build.items != null && tile.build.items.total() > 0) {
                        float dist = unit.dst(tile.worldx(), tile.worldy());
                        if (dist < nearestDist) {
                            nearestDist = dist;
                            nearest = tile;
                        }
                    }
                }
            }
        }
        
        return nearest;
    }
    
    void deliverToCore(CoreBuild core) {
        target.set(core.x, core.y);
        moveToTarget();
        
        if (unit.within(core, unit.type.range)) {
            if (unit.stack.amount > 0) {
                core.items.add(unit.stack.item, unit.stack.amount);
            }
            unit.clearItem();
        }
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
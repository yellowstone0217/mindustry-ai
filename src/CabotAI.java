package patrol;

import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.entities.units.AIController;
import mindustry.game.*;
import mindustry.game.Schematic.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.production.*;
import mindustry.world.blocks.storage.CoreBlock.*;

public class CabotAI extends AIController {
    Vec2 home = new Vec2();
    Vec2 target = new Vec2();
    
    Seq<Schematic> blueprints = new Seq<>();
    Seq<String> blueprintNames = new Seq<>();
    int blueprintIndex = 0;
    Interval timer = new Interval(4);
    static final int timerBuild = 0;
    static final float buildInterval = 120f;
    static final int maxAttempts = 8;
    
    float idleTimer = 0f;
    Vec2 patrolPoint = new Vec2();
    Schematic currentSchematic = null;
    int currentOriginX = 0;
    int currentOriginY = 0;
    int currentTileIndex = 0;
    boolean building = false;
    float buildTimer = 0f;
    
    public CabotAI(float hx, float hy) {
        home.set(hx, hy);
        patrolPoint.set(hx, hy);
        target.set(hx, hy);
        loadBlueprints();
    }
    
    void loadBlueprints() {
        blueprints.clear();
        blueprintNames.clear();
        
        try {
            var mod = Vars.mods.getMod("sky-raider");
            if (mod == null) return;
            
            var blueprintDir = mod.root.child("Blueprints");
            if (!blueprintDir.exists() || !blueprintDir.isDirectory()) {
                return;
            }
            
            var files = blueprintDir.list();
            for (int i = 0; i < files.length; i++) {
                var file = files[i];
                String name = file.name();
                if (name.endsWith(".msch")) {
                    try {
                        Schematic schematic = Schematics.read(file);
                        blueprints.add(schematic);
                        blueprintNames.add(name);
                    } catch (Exception e) {}
                }
            }
        } catch (Exception e) {}
    }
    
    @Override
    public void updateUnit() {
        if (unit == null || unit.dead()) return;
        
        if (unit.isPlayer() || unit.getPlayer() != null) {
            unit.controller(this);
            return;
        }
        
        CoreBuild core = unit.closestCore();
        if (core != null && core.health < core.maxHealth * 0.95f) {
            repairCore(core);
            return;
        }
        
        if (building && currentSchematic != null) {
            continueBuilding(core);
            return;
        }
        
        if (blueprints.size > 0 && timer.get(timerBuild, buildInterval)) {
            boolean started = false;
            for (int i = 0; i < maxAttempts; i++) {
                Schematic schem = blueprints.get(blueprintIndex % blueprints.size);
                String name = blueprintNames.get(blueprintIndex % blueprints.size);
                blueprintIndex++;
                if (tryStartBuilding(schem, name, core)) {
                    started = true;
                    break;
                }
            }
            if (!started) {
                patrol();
            }
        } else if (blueprints.isEmpty()) {
            patrol();
        }
        
        if (!building) {
            moveToTarget();
        }
    }
    
    boolean tryStartBuilding(Schematic schem, String name, CoreBuild core) {
        if (core == null) return false;
        
        int range = 60;
        float angle = (float)(Math.random() * 360f);
        float dist = 15f + (float)(Math.random() * (range - 15f));
        
        int wx = core.tileX() + (int)(Math.cos(Math.toRadians(angle)) * dist);
        int wy = core.tileY() + (int)(Math.sin(Math.toRadians(angle)) * dist);
        
        if (!checkBlueprintResources(schem, name, wx, wy)) {
            return false;
        }
        
        for (int i = 0; i < schem.tiles.size; i++) {
            var tile = schem.tiles.get(i);
            int realX = tile.x + wx;
            int realY = tile.y + wy;
            
            if (!Build.validPlace(tile.block, unit.team, realX, realY, tile.rotation)) {
                return false;
            }
        }
        
        currentSchematic = schem;
        currentOriginX = wx;
        currentOriginY = wy;
        currentTileIndex = 0;
        building = true;
        buildTimer = 0f;
        
        return true;
    }
    
    boolean isPowerNode(Block block) {
        if (block == null || block.name == null) return false;
        String name = block.name.toLowerCase();
        return name.contains("power-node");
    }
    
    boolean checkBlueprintResources(Schematic schem, String name, int originX, int originY) {
        for (int i = 0; i < schem.tiles.size; i++) {
            var tile = schem.tiles.get(i);
            int realX = tile.x + originX;
            int realY = tile.y + originY;
            
            Tile worldTile = Vars.world.tile(realX, realY);
            if (worldTile == null) continue;
            
            boolean isDrill = false;
            if (tile.block != null && tile.block.name != null) {
                String blockName = tile.block.name.toLowerCase();
                if (blockName.contains("drill") || blockName.contains("ore")) {
                    isDrill = true;
                }
            }
            
            if (isDrill) {
                Item drop = worldTile.drop();
                if (drop == null) return false;
                
                String lowerName = name.toLowerCase();
                if (lowerName.contains("石墨")) {
                    if (drop != Items.coal) return false;
                } else if (lowerName.contains("防御散射")) {
                    if (drop != Items.lead) return false;
                }
            }
        }
        return true;
    }
    
    int getNextTileIndex() {
        // 先找非电力节点
        for (int i = currentTileIndex; i < currentSchematic.tiles.size; i++) {
            var tile = currentSchematic.tiles.get(i);
            if (!isPowerNode(tile.block)) {
                return i;
            }
        }
        
        // 再找电力节点
        for (int i = 0; i < currentSchematic.tiles.size; i++) {
            var tile = currentSchematic.tiles.get(i);
            if (isPowerNode(tile.block)) {
                return i;
            }
        }
        
        return currentSchematic.tiles.size;
    }
    
    void continueBuilding(CoreBuild core) {
        if (currentSchematic == null) {
            building = false;
            return;
        }
        
        int nextIndex = getNextTileIndex();
        
        if (nextIndex >= currentSchematic.tiles.size) {
            // 全部造完
            building = false;
            currentSchematic = null;
            currentTileIndex = 0;
            return;
        }
        
        currentTileIndex = nextIndex;
        var tile = currentSchematic.tiles.get(currentTileIndex);
        int realX = tile.x + currentOriginX;
        int realY = tile.y + currentOriginY;
        
        Tile worldTile = Vars.world.tile(realX, realY);
        if (worldTile == null || worldTile.build != null) {
            currentTileIndex++;
            buildTimer = 0f;
            return;
        }
        
        target.set(worldTile.worldx(), worldTile.worldy());
        moveToTarget();
        
        if (unit.within(worldTile, 30f)) {
            buildTimer += 60f;
            float buildTime = tile.block.buildTime / unit.type.buildSpeed;
            if (buildTimer >= buildTime) {
                Call.setTile(worldTile, tile.block, unit.team, tile.rotation);
                buildTimer = 0f;
                currentTileIndex++;
            }
        }
    }
    
    void repairCore(CoreBuild core) {
        target.set(core.x, core.y);
        unit.lookAt(core);
        moveToTarget();
    }
    
    void patrol() {
        idleTimer++;
        if (idleTimer > 120f || unit.dst(patrolPoint) < 30f) {
            idleTimer = 0f;
            float angle = (float)(Math.random() * 360f);
            float dist = 40f + (float)(Math.random() * 80f);
            patrolPoint.set(
                home.x + (float)Math.cos(Math.toRadians(angle)) * dist,
                home.y + (float)Math.sin(Math.toRadians(angle)) * dist
            );
        }
        target.set(patrolPoint);
        unit.controlWeapons(false, false);
    }
    
    void moveToTarget() {
        float dx = target.x - unit.x;
        float dy = target.y - unit.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist < 5f || dist <= 0) return;
        
        float speed = unit.speed() * 0.5f;
        unit.vel.x = (dx / dist) * speed;
        unit.vel.y = (dy / dist) * speed;
        unit.rotation = (float) Math.toDegrees(Math.atan2(dy, dx));
    }
}
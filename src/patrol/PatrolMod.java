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

import arc.Events;
import arc.math.geom.Vec2;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.EventType.UnitDestroyEvent;
import mindustry.game.Team;
import mindustry.gen.Unit;
import mindustry.mod.Mod;
import mindustry.type.UnitType;
import arc.func.Cons;
import mindustry.world.blocks.storage.CoreBlock;

public class PatrolMod extends Mod {
    static final String UNIT_NAME = "sky-raider-探查者";
    static final String BOT_NAME = "sky-raider-初号机BOT";
    static final String CABOT_NAME = "sky-raider-CABOT";
    static final String BCR_NAME = "sky-raider-BCRBOT";
    static final String SUPPLY_NAME = "sky-raider-供应者";
    static final String TRANSPORTER_NAME = "sky-raider-搬运工";
    
    static final int MAX_UNITS = 2;
    static final int MAX_BCR = 2;
    static final int MAX_SUPPLY = 3;
    static final int MAX_TRANSPORTER = 2;
    
    static final float PATROL_RANGE = 200f;
    static final float ATTACK_RANGE = 200f;
    static final Team OWNER_TEAM = Team.sharded;
    
    boolean worldReady = false;
    boolean botSpawned = false;
    boolean cabotSpawned = false;
    boolean bcrSpawned = false;
    boolean supplySpawned = false;
    boolean transporterSpawned = false;

    float homeX = 0f;
    float homeY = 0f;

    // 初号机追踪
    Unit botUnit = null;
    int botUnitID = -1;
    float botRespawnTimer = 0f;
    final float BOT_RESPAWN_DELAY = 300f;

    // CABOT追踪
    Unit cabotUnit = null;
    int cabotUnitID = -1;
    float cabotRespawnTimer = 0f;
    final float CABOT_RESPAWN_DELAY = 300f;

    // BCRBOT追踪
    Unit bcrUnit = null;
    int bcrUnitID = -1;
    float bcrRespawnTimer = 0f;
    final float BCR_RESPAWN_DELAY = 300f;
    int bcrCount = 0;
    
    // 供应者追踪
    Seq<Unit> supplyUnits = new Seq<>(3);
    int supplyCount = 0;
    float supplyRespawnTimer = 0f;
    final float SUPPLY_RESPAWN_DELAY = 300f;
    
    // 搬运工追踪
    Seq<Unit> transporterUnits = new Seq<>(2);
    int transporterCount = 0;
    float transporterRespawnTimer = 0f;
    final float TRANSPORTER_RESPAWN_DELAY = 300f;

    public PatrolMod() {
        Events.on(WorldLoadEvent.class, new Cons<WorldLoadEvent>() {
            @Override
            public void get(WorldLoadEvent event) {
                onWorldLoad();
            }
        });

        Events.on(UnitDestroyEvent.class, new Cons<UnitDestroyEvent>() {
            @Override
            public void get(UnitDestroyEvent event) {
                onUnitDestroy(event.unit);
            }
        });

        Timer.schedule(new Runnable() {
            @Override
            public void run() {
                updateBot();
            }
        }, 0f, 1f);

        Timer.schedule(new Runnable() {
            @Override
            public void run() {
                updateCabot();
            }
        }, 0f, 1f);

        Timer.schedule(new Runnable() {
            @Override
            public void run() {
                updateBcr();
            }
        }, 0f, 1f);
        
        Timer.schedule(new Runnable() {
            @Override
            public void run() {
                updateSupply();
            }
        }, 0f, 1f);
        
        Timer.schedule(new Runnable() {
            @Override
            public void run() {
                updateTransporter();
            }
        }, 0f, 1f);
    }

    void onWorldLoad() {
        worldReady = false;
        botSpawned = false;
        cabotSpawned = false;
        bcrSpawned = false;
        supplySpawned = false;
        transporterSpawned = false;
        
        botUnit = null;
        botUnitID = -1;
        botRespawnTimer = 0f;
        
        cabotUnit = null;
        cabotUnitID = -1;
        cabotRespawnTimer = 0f;
        
        bcrUnit = null;
        bcrUnitID = -1;
        bcrRespawnTimer = 0f;
        bcrCount = 0;
        
        supplyUnits.clear();
        supplyCount = 0;
        supplyRespawnTimer = 0f;
        
        transporterUnits.clear();
        transporterCount = 0;
        transporterRespawnTimer = 0f;

        Timer.schedule(new Runnable() {
            @Override
            public void run() {
                findHome();
                killAllPatrolUnits();

                Timer.schedule(new Runnable() {
                    @Override
                    public void run() {
                        doInitialSpawn();
                        worldReady = true;
                    }
                }, 0.5f);
            }
        }, 3f);
    }

    void findHome() {
        Seq<CoreBlock.CoreBuild> cores = OWNER_TEAM.cores();
        if (cores != null && cores.size > 0) {
            CoreBlock.CoreBuild core = cores.first();
            homeX = core.x;
            homeY = core.y;
        }
    }

    void doInitialSpawn() {
        if (Vars.net.client()) return;

        UnitType type = findUnitType();
        if (type != null) {
            int current = countAlive();
            for (int i = current; i < MAX_UNITS; i++) {
                spawnUnit(type);
            }
        }

        if (!botSpawned) {
            spawnBot();
            botSpawned = true;
        }

        if (!cabotSpawned) {
            spawnCabot();
            cabotSpawned = true;
        }

        if (!bcrSpawned) {
            for (int i = 0; i < MAX_BCR; i++) {
                spawnBcr();
            }
            bcrSpawned = true;
        }
        
        if (!supplySpawned) {
            spawnSupplyUnits();
            supplySpawned = true;
        }
        
        if (!transporterSpawned) {
            spawnTransporters();
            transporterSpawned = true;
        }
    }

    // ==================== 探查者 ====================
    void spawnUnit(UnitType type) {
        if (Vars.net.client()) return;
        float sx = homeX + (float)(Math.random() * 80 - 40);
        float sy = homeY + (float)(Math.random() * 80 - 40);
        Unit unit = type.create(OWNER_TEAM);
        unit.set(sx, sy);
        unit.add();
        startPatrol(unit);
    }

    int countAlive() {
        int count = 0;
        Object[] units = OWNER_TEAM.data().units.toArray();
        for (int i = 0; i < units.length; i++) {
            Unit u = (Unit) units[i];
            if (u != null && !u.dead() && UNIT_NAME.equals(u.type.name)) {
                count++;
            }
        }
        return count;
    }

    // ==================== 初号机 ====================
    void spawnBot() {
        UnitType botType = findUnitType(BOT_NAME);
        if (botType == null) return;
        Unit bot = botType.create(OWNER_TEAM);
        bot.set(homeX + 30, homeY + 30);
        bot.controller(new BotAI(homeX, homeY));
        bot.add();
        botUnit = bot;
        botUnitID = bot.id;
        botRespawnTimer = 0f;
    }

    void updateBot() {
        Unit alive = null;
        int count = 0;
        Object[] units = OWNER_TEAM.data().units.toArray();
        for (int i = 0; i < units.length; i++) {
            Unit u = (Unit) units[i];
            if (u != null && !u.dead() && BOT_NAME.equals(u.type.name)) {
                count++;
                if (u.getPlayer() == null && alive == null) {
                    alive = u;
                }
            }
        }
        if (count > 1 && alive != null) {
            for (int i = 0; i < units.length; i++) {
                Unit u = (Unit) units[i];
                if (u != null && !u.dead() && BOT_NAME.equals(u.type.name) && u.id != alive.id) {
                    u.remove();
                }
            }
        }
        if (alive != null) {
            if (alive.controller() != null && !alive.controller().getClass().getName().contains("BotAI")) {
                alive.controller(new BotAI(homeX, homeY));
            }
            botUnit = alive;
            botUnitID = alive.id;
            botRespawnTimer = 0f;
        } else {
            botUnit = null;
            botUnitID = -1;
        }
        if (botUnit == null) {
            botRespawnTimer += 60f;
            if (botRespawnTimer >= BOT_RESPAWN_DELAY) {
                spawnBot();
            }
        }
    }

    // ==================== CABOT ====================
    void spawnCabot() {
        UnitType cabotType = findUnitType(CABOT_NAME);
        if (cabotType == null) return;
        Unit cabot = cabotType.create(OWNER_TEAM);
        cabot.set(homeX + 50, homeY + 50);
        cabot.controller(new CabotAI(homeX, homeY));
        cabot.add();
        cabotUnit = cabot;
        cabotUnitID = cabot.id;
        cabotRespawnTimer = 0f;
    }

    void updateCabot() {
        Unit alive = null;
        int count = 0;
        Object[] units = OWNER_TEAM.data().units.toArray();
        for (int i = 0; i < units.length; i++) {
            Unit u = (Unit) units[i];
            if (u != null && !u.dead() && CABOT_NAME.equals(u.type.name)) {
                count++;
                if (u.getPlayer() == null && alive == null) {
                    alive = u;
                }
            }
        }
        if (count > 1 && alive != null) {
            for (int i = 0; i < units.length; i++) {
                Unit u = (Unit) units[i];
                if (u != null && !u.dead() && CABOT_NAME.equals(u.type.name) && u.id != alive.id) {
                    u.remove();
                }
            }
        }
        if (alive != null) {
            if (alive.controller() != null && !alive.controller().getClass().getName().contains("CabotAI")) {
                alive.controller(new CabotAI(homeX, homeY));
            }
            cabotUnit = alive;
            cabotUnitID = alive.id;
            cabotRespawnTimer = 0f;
        } else {
            cabotUnit = null;
            cabotUnitID = -1;
        }
        if (cabotUnit == null) {
            cabotRespawnTimer += 60f;
            if (cabotRespawnTimer >= CABOT_RESPAWN_DELAY) {
                spawnCabot();
            }
        }
    }

    // ==================== BCRBOT ====================
    void spawnBcr() {
        if (bcrCount >= MAX_BCR) return;
        UnitType bcrType = findUnitType(BCR_NAME);
        if (bcrType == null) return;
        Unit bcr = bcrType.create(OWNER_TEAM);
        bcr.set(homeX + 70 + bcrCount * 30, homeY + 70);
        bcr.controller(new BcrAI(homeX, homeY));
        bcr.add();
        bcrCount++;
    }

    void updateBcr() {
        int aliveCount = 0;
        Unit aliveOne = null;
        Object[] units = OWNER_TEAM.data().units.toArray();
        for (int i = 0; i < units.length; i++) {
            Unit u = (Unit) units[i];
            if (u != null && !u.dead() && BCR_NAME.equals(u.type.name) && u.getPlayer() == null) {
                aliveCount++;
                if (aliveOne == null) aliveOne = u;
            }
        }
        
        if (aliveCount > MAX_BCR) {
            int removed = 0;
            for (int i = 0; i < units.length; i++) {
                Unit u = (Unit) units[i];
                if (u != null && !u.dead() && BCR_NAME.equals(u.type.name) && u.getPlayer() == null) {
                    if (removed < aliveCount - MAX_BCR) {
                        u.remove();
                        removed++;
                    }
                }
            }
        }
        
        for (int i = 0; i < units.length; i++) {
            Unit u = (Unit) units[i];
            if (u != null && !u.dead() && BCR_NAME.equals(u.type.name) && u.getPlayer() == null) {
                if (u.controller() != null && !u.controller().getClass().getName().contains("BcrAI")) {
                    u.controller(new BcrAI(homeX, homeY));
                }
            }
        }
        
        bcrCount = aliveCount;
        bcrUnit = aliveOne;
        if (bcrUnit != null) {
            bcrUnitID = bcrUnit.id;
            bcrRespawnTimer = 0f;
        } else {
            bcrRespawnTimer += 60f;
            if (bcrRespawnTimer >= BCR_RESPAWN_DELAY) {
                for (int i = bcrCount; i < MAX_BCR; i++) {
                    spawnBcr();
                }
                bcrRespawnTimer = 0f;
            }
        }
    }
    
    // ==================== 供应者 ====================
    void spawnSupplyUnits() {
        if (Vars.net.client()) return;
        
        UnitType supplyType = findUnitType(SUPPLY_NAME);
        if (supplyType == null) return;
        
        for (int i = 0; i < supplyUnits.size; i++) {
            Unit u = supplyUnits.get(i);
            if (u != null && !u.dead()) u.remove();
        }
        supplyUnits.clear();
        supplyCount = 0;
        
        for (int i = 0; i < MAX_SUPPLY; i++) {
            Unit supply = supplyType.create(OWNER_TEAM);
            supply.set(homeX + 20 * i, homeY + 20);
            supply.controller(new SupplyAI(homeX, homeY));
            supply.add();
            supplyUnits.add(supply);
            supplyCount++;
        }
    }

    void updateSupply() {
        int aliveCount = 0;
        for (int i = supplyUnits.size - 1; i >= 0; i--) {
            Unit u = supplyUnits.get(i);
            if (u == null || u.dead()) {
                supplyUnits.remove(i);
                continue;
            }
            aliveCount++;
            if (u.controller() == null || 
                !u.controller().getClass().getName().contains("SupplyAI")) {
                u.controller(new SupplyAI(homeX, homeY));
            }
        }
        
        supplyCount = aliveCount;
        
        if (supplyCount < MAX_SUPPLY && supplyUnits.size < MAX_SUPPLY) {
            supplyRespawnTimer += 60f;
            if (supplyRespawnTimer >= SUPPLY_RESPAWN_DELAY) {
                UnitType supplyType = findUnitType(SUPPLY_NAME);
                if (supplyType != null) {
                    int need = MAX_SUPPLY - supplyCount;
                    for (int i = 0; i < need; i++) {
                        Unit supply = supplyType.create(OWNER_TEAM);
                        supply.set(homeX + Mathf.random(-20f, 20f), 
                                  homeY + Mathf.random(-20f, 20f));
                        supply.controller(new SupplyAI(homeX, homeY));
                        supply.add();
                        supplyUnits.add(supply);
                        supplyCount++;
                    }
                    supplyRespawnTimer = 0f;
                }
            }
        }
    }
    
    // ==================== 搬运工 ====================
    void spawnTransporters() {
        if (Vars.net.client()) return;
        
        UnitType transporterType = findUnitType(TRANSPORTER_NAME);
        if (transporterType == null) return;
        
        for (int i = 0; i < transporterUnits.size; i++) {
            Unit u = transporterUnits.get(i);
            if (u != null && !u.dead()) u.remove();
        }
        transporterUnits.clear();
        transporterCount = 0;
        
        for (int i = 0; i < MAX_TRANSPORTER; i++) {
            Unit t = transporterType.create(OWNER_TEAM);
            t.set(homeX + 30 * i, homeY + 30);
            t.controller(new TransporterAI(homeX, homeY));
            t.add();
            transporterUnits.add(t);
            transporterCount++;
        }
    }

    void updateTransporter() {
        int aliveCount = 0;
        for (int i = transporterUnits.size - 1; i >= 0; i--) {
            Unit u = transporterUnits.get(i);
            if (u == null || u.dead()) {
                transporterUnits.remove(i);
                continue;
            }
            aliveCount++;
            if (u.controller() == null || 
                !u.controller().getClass().getName().contains("TransporterAI")) {
                u.controller(new TransporterAI(homeX, homeY));
            }
        }
        
        transporterCount = aliveCount;
        
        if (transporterCount < MAX_TRANSPORTER && transporterUnits.size < MAX_TRANSPORTER) {
            transporterRespawnTimer += 60f;
            if (transporterRespawnTimer >= TRANSPORTER_RESPAWN_DELAY) {
                UnitType transporterType = findUnitType(TRANSPORTER_NAME);
                if (transporterType != null) {
                    int need = MAX_TRANSPORTER - transporterCount;
                    for (int i = 0; i < need; i++) {
                        Unit t = transporterType.create(OWNER_TEAM);
                        t.set(homeX + Mathf.random(-20f, 20f), 
                              homeY + Mathf.random(-20f, 20f));
                        t.controller(new TransporterAI(homeX, homeY));
                        t.add();
                        transporterUnits.add(t);
                        transporterCount++;
                    }
                    transporterRespawnTimer = 0f;
                }
            }
        }
    }

    // ==================== 共通 ====================
    void killAllPatrolUnits() {
        Object[] units = OWNER_TEAM.data().units.toArray();
        for (int i = 0; i < units.length; i++) {
            Unit u = (Unit) units[i];
            if (u != null && !u.dead()) {
                String name = u.type.name;
                if (UNIT_NAME.equals(name) || BOT_NAME.equals(name) || 
                    CABOT_NAME.equals(name) || BCR_NAME.equals(name) ||
                    SUPPLY_NAME.equals(name) || TRANSPORTER_NAME.equals(name)) {
                    u.kill();
                }
            }
        }
        supplyUnits.clear();
        supplyCount = 0;
        transporterUnits.clear();
        transporterCount = 0;
    }

    void onUnitDestroy(Unit unit) {
        if (unit == null || unit.type == null) return;
        if (unit.team != OWNER_TEAM) return;
        if (Vars.net.client()) return;
        if (!worldReady) return;

        if (UNIT_NAME.equals(unit.type.name)) {
            Timer.schedule(new Runnable() {
                @Override
                public void run() {
                    if (!worldReady) return;
                    UnitType type = findUnitType();
                    if (type == null) return;
                    int current = countAlive();
                    if (current < MAX_UNITS) {
                        spawnUnit(type);
                    }
                }
            }, 5f);
        }
    }

    UnitType findUnitType() {
        for (UnitType t : Vars.content.units()) {
            if (UNIT_NAME.equals(t.name)) {
                return t;
            }
        }
        return null;
    }

    UnitType findUnitType(String name) {
        for (UnitType t : Vars.content.units()) {
            if (name.equals(t.name)) {
                return t;
            }
        }
        return null;
    }

    Unit findEnemy(Unit me) {
        Unit closest = null;
        float closestDist = ATTACK_RANGE;
        for (Team team : Team.all) {
            if (team == me.team) continue;
            Object[] units = team.data().units.toArray();
            for (int i = 0; i < units.length; i++) {
                Unit u = (Unit) units[i];
                if (u == null || u.dead()) continue;
                if (!u.type.targetable) continue;
                float dx = u.x - me.x;
                float dy = u.y - me.y;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = u;
                }
            }
        }
        return closest;
    }

    void startPatrol(Unit unit) {
        Timer.Task patrolTask = new Timer.Task() {
            Vec2 target = new Vec2();
            float changeTimer = 0f;
            Unit enemy = null;
            boolean attacking = false;
            {
                pickTarget();
            }
            void pickTarget() {
                float angle = (float)(Math.random() * Math.PI * 2);
                float dist = 50f + (float)(Math.random() * PATROL_RANGE);
                target.set(
                    homeX + (float)Math.cos(angle) * dist,
                    homeY + (float)Math.sin(angle) * dist
                );
                changeTimer = 0f;
            }
            @Override
            public void run() {
                if (unit == null || unit.dead()) {
                    this.cancel();
                    return;
                }
                enemy = findEnemy(unit);
                if (enemy != null) {
                    attacking = true;
                    target.set(enemy.x, enemy.y);
                    changeTimer = 0f;
                } else if (attacking) {
                    attacking = false;
                    pickTarget();
                }
                if (!attacking) {
                    float dxHome = unit.x - homeX;
                    float dyHome = unit.y - homeY;
                    float distHome = (float)Math.sqrt(dxHome * dxHome + dyHome * dyHome);
                    if (distHome > PATROL_RANGE) {
                        target.set(homeX, homeY);
                        changeTimer = 0f;
                    } else {
                        changeTimer += 0.5f;
                    }
                }
                float dx = target.x - unit.x;
                float dy = target.y - unit.y;
                float dist = (float)Math.sqrt(dx * dx + dy * dy);
                if (!attacking && (dist < 30f || changeTimer > 8f)) {
                    pickTarget();
                    dx = target.x - unit.x;
                    dy = target.y - unit.y;
                    dist = (float)Math.sqrt(dx * dx + dy * dy);
                }
                if (dist > 0) {
                    float speed = unit.speed();
                    if (attacking) {
                        speed *= 0.7f;
                    }
                    unit.vel.x = (dx / dist) * speed;
                    unit.vel.y = (dy / dist) * speed;
                    unit.rotation = (float)Math.toDegrees(Math.atan2(dy, dx));
                }
                if (attacking && enemy != null && dist < ATTACK_RANGE) {
                    unit.aim(enemy);
                }
            }
        };
        Timer.schedule(patrolTask, 0f, 0.5f);
    }
}
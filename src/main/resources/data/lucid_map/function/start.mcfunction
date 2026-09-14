# Run AS and AT the player who should perform the map scan.
function lucid_map:apply_config

# Clean up prior scan state.
scoreboard players set #running lucid_map 0
kill @e[type=minecraft:marker,tag=lucid_map_center]
kill @e[type=minecraft:marker,tag=lucid_map_cursor]
tag @a remove lucid_map_target

# Remember scanning player and exact return position.
tag @s add lucid_map_target
summon minecraft:marker ~ ~ ~ {Tags:["lucid_map_center"]}
execute at @e[type=minecraft:marker,tag=lucid_map_center,limit=1] run forceload add ~ ~

# Capture the center X/Z as integer block coordinates.
execute store result score #center_x lucid_map run data get entity @e[type=minecraft:marker,tag=lucid_map_center,limit=1] Pos[0] 1
execute store result score #center_z lucid_map run data get entity @e[type=minecraft:marker,tag=lucid_map_center,limit=1] Pos[2] 1

# Compute north-west grid point from the requested rectangle.
scoreboard players operation #half_x lucid_map = #last_col lucid_map
scoreboard players operation #half_x lucid_map /= #two lucid_map
scoreboard players operation #half_x lucid_map *= #spacing lucid_map

scoreboard players operation #half_z lucid_map = #last_row lucid_map
scoreboard players operation #half_z lucid_map /= #two lucid_map
scoreboard players operation #half_z lucid_map *= #spacing lucid_map

scoreboard players operation #x lucid_map = #center_x lucid_map
scoreboard players operation #x lucid_map -= #half_x lucid_map
scoreboard players operation #z lucid_map = #center_z lucid_map
scoreboard players operation #z lucid_map -= #half_z lucid_map

# Traversal state.
scoreboard players set #col lucid_map 0
scoreboard players set #row lucid_map 0
scoreboard players set #dir lucid_map 1
scoreboard players set #visited lucid_map 1
scoreboard players set #timer lucid_map 0
scoreboard players set #running lucid_map 1

# First stop happens immediately.
function lucid_map:teleport_target

tellraw @s [{"text":"[LucidMap] Sweep started: ","color":"green"},{"score":{"name":"#area_x","objective":"lucid_map"}},{"text":" x "},{"score":{"name":"#area_z","objective":"lucid_map"}},{"text":" blocks, "},{"score":{"name":"#interval_ms","objective":"lucid_map"}},{"text":" ms ("},{"score":{"name":"#interval_ticks","objective":"lucid_map"}},{"text":" ticks) per stop."}]
tellraw @s [{"text":"[LucidMap] Grid: ","color":"aqua"},{"score":{"name":"#cols","objective":"lucid_map"}},{"text":" x "},{"score":{"name":"#rows","objective":"lucid_map"}},{"text":" = "},{"score":{"name":"#points","objective":"lucid_map"}},{"text":" unique stops."}]
tellraw @s {"text":"[LucidMap] Keep Xaero's World Map enabled and stay connected. /function lucid_map:stop cancels.","color":"yellow"}

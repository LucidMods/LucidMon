scoreboard players set #advanced lucid_map 1
scoreboard players set #timer lucid_map 0

# Move exactly one new 256-block grid cell.
execute if score #dir lucid_map matches 1 run scoreboard players operation #x lucid_map += #spacing lucid_map
execute if score #dir lucid_map matches -1 run scoreboard players operation #x lucid_map -= #spacing lucid_map

scoreboard players add #col lucid_map 1
scoreboard players add #visited lucid_map 1
function lucid_map:teleport_target

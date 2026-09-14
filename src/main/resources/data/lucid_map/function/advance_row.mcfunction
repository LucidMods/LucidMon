scoreboard players set #advanced lucid_map 1
scoreboard players set #timer lucid_map 0

# Drop one row south. X stays at the current edge; then direction reverses.
scoreboard players operation #z lucid_map += #spacing lucid_map
scoreboard players add #row lucid_map 1
scoreboard players set #col lucid_map 0
scoreboard players operation #dir lucid_map *= #negone lucid_map

scoreboard players add #visited lucid_map 1
function lucid_map:teleport_target

# Copy current scoreboard coordinates into command storage for the macro.
execute store result storage lucid_map:runtime x int 1 run scoreboard players get #x lucid_map
execute store result storage lucid_map:runtime z int 1 run scoreboard players get #z lucid_map
function lucid_map:teleport_macro with storage lucid_map:runtime

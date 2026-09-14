# LucidMap bootstrap.
scoreboard objectives add lucid_map dummy

# Persistent defaults. These are only created if the values do not already
# exist in command storage, so player/admin changes survive reloads/restarts.
execute unless data storage lucid_map:config area.x run data modify storage lucid_map:config area.x set value 10000
execute unless data storage lucid_map:config area.z run data modify storage lucid_map:config area.z set value 10000
execute unless data storage lucid_map:config interval_ms run data modify storage lucid_map:config interval_ms set value 20000

# Fixed scan-grid spacing. 256 blocks matches the previous LucidMap sweeper.
scoreboard players set #spacing lucid_map 256
scoreboard players set #spacing_minus_one lucid_map 255
scoreboard players set #fifty lucid_map 50
scoreboard players set #fortynine lucid_map 49
scoreboard players set #two lucid_map 2
scoreboard players set #negone lucid_map -1

# Never resume an interrupted scan merely because the datapack was reloaded.
scoreboard players set #running lucid_map 0
scoreboard players set #timer lucid_map 0

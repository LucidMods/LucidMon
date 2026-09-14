scoreboard players set @s pk_restored 0
function soulpack:place_recovery_in_inventory

execute if score @s pk_restored matches 1 run kill @e[type=minecraft:item,tag=soulpack_recovery_item,limit=1]
execute if score @s pk_restored matches 1 run tellraw @s {"text":"Recovered backpack copied into your inventory.","color":"green"}

execute if entity @e[type=minecraft:item,tag=soulpack_recovery_item,limit=1] if score @s pk_restored matches 0 run data modify entity @e[type=minecraft:item,tag=soulpack_recovery_item,limit=1] Owner set from entity @s UUID
execute if entity @e[type=minecraft:item,tag=soulpack_recovery_item,limit=1] if score @s pk_restored matches 0 run data merge entity @e[type=minecraft:item,tag=soulpack_recovery_item,limit=1] {Age:-32768s,PickupDelay:0s,Invulnerable:1b}
execute if entity @e[type=minecraft:item,tag=soulpack_recovery_item,limit=1] if score @s pk_restored matches 0 run tp @e[type=minecraft:item,tag=soulpack_recovery_item,limit=1] @s
execute if entity @e[type=minecraft:item,tag=soulpack_recovery_item,limit=1] if score @s pk_restored matches 0 run tellraw @s {"text":"Inventory full; recovered backpack placed safely at your feet.","color":"yellow"}

tag @e[type=minecraft:item,tag=soulpack_recovery_item] remove soulpack_recovery_item

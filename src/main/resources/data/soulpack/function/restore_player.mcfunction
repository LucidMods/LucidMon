scoreboard players operation #restore pk_global = @s pk_owner
tag @s add soulpack_restoring_player
execute as @e[type=minecraft:item,tag=soulpack] if score @s pk_owner = #restore pk_global run tag @s add soulpack_restoring_item

execute if entity @e[type=minecraft:item,tag=soulpack_restoring_item,limit=1] run scoreboard players set @s pk_restored 0
execute if entity @e[type=minecraft:item,tag=soulpack_restoring_item,limit=1] run function soulpack:place_in_inventory

# Exact stack copied successfully; only now delete the ground entity.
execute if score @s pk_restored matches 1 run kill @e[type=minecraft:item,tag=soulpack_restoring_item,limit=1]
execute if score @s pk_restored matches 1 run scoreboard players set @s pk_pending 0
execute if score @s pk_restored matches 1 run tellraw @s {"text":"Soulpack: exact backpack restored directly into your inventory.","color":"green"}

# Direct inventory insertion failed. Keep original safe and make it immediately pickup-able.
execute if entity @e[type=minecraft:item,tag=soulpack_restoring_item,limit=1] if score @s pk_restored matches 0 run data modify entity @e[type=minecraft:item,tag=soulpack_restoring_item,limit=1] Owner set from entity @s UUID
execute if entity @e[type=minecraft:item,tag=soulpack_restoring_item,limit=1] if score @s pk_restored matches 0 run data merge entity @e[type=minecraft:item,tag=soulpack_restoring_item,limit=1] {Age:-32768s,PickupDelay:0s,Invulnerable:1b}
execute if entity @e[type=minecraft:item,tag=soulpack_restoring_item,limit=1] if score @s pk_restored matches 0 run tp @e[type=minecraft:item,tag=soulpack_restoring_item,limit=1] @s
execute if entity @e[type=minecraft:item,tag=soulpack_restoring_item,limit=1] if score @s pk_restored matches 0 run scoreboard players set @s pk_pending 0
execute if entity @e[type=minecraft:item,tag=soulpack_restoring_item,limit=1] if score @s pk_restored matches 0 run tellraw @s {"text":"Soulpack: direct inventory insertion failed; the protected backpack was placed at your feet for pickup.","color":"yellow"}

tag @e[type=minecraft:item,tag=soulpack_restoring_item] remove soulpack_restoring_item
tag @s remove soulpack_restoring_player

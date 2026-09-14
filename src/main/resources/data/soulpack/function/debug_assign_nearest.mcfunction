scoreboard players add #next pk_global 1
scoreboard players operation @s pk_owner = #next pk_global
scoreboard players set @s pk_pending 1
tag @s add soulpack_debug_owner

tag @e[type=minecraft:item,tag=soulpack_debug_candidate] remove soulpack_debug_candidate
execute as @e[type=minecraft:item,sort=nearest,limit=1] if items entity @s contents #minecraft:air run tag @s add soulpack_debug_candidate
# Tier-specific candidate tagging
execute as @e[type=minecraft:item,sort=nearest,limit=1] if items entity @s contents sophisticatedbackpacks:backpack run tag @s add soulpack_debug_candidate
execute as @e[type=minecraft:item,sort=nearest,limit=1] if items entity @s contents sophisticatedbackpacks:copper_backpack run tag @s add soulpack_debug_candidate
execute as @e[type=minecraft:item,sort=nearest,limit=1] if items entity @s contents sophisticatedbackpacks:iron_backpack run tag @s add soulpack_debug_candidate
execute as @e[type=minecraft:item,sort=nearest,limit=1] if items entity @s contents sophisticatedbackpacks:gold_backpack run tag @s add soulpack_debug_candidate
execute as @e[type=minecraft:item,sort=nearest,limit=1] if items entity @s contents sophisticatedbackpacks:diamond_backpack run tag @s add soulpack_debug_candidate
execute as @e[type=minecraft:item,sort=nearest,limit=1] if items entity @s contents sophisticatedbackpacks:netherite_backpack run tag @s add soulpack_debug_candidate

execute as @e[type=minecraft:item,tag=soulpack_debug_candidate,sort=nearest,limit=1] run scoreboard players operation @s pk_owner = @a[tag=soulpack_debug_owner,limit=1] pk_owner
tag @e[type=minecraft:item,tag=soulpack_debug_candidate,sort=nearest,limit=1] add soulpack
data merge entity @e[type=minecraft:item,tag=soulpack_debug_candidate,sort=nearest,limit=1] {Age:-32768s,PickupDelay:32767s,Invulnerable:1b}
execute if entity @e[type=minecraft:item,tag=soulpack_debug_candidate,limit=1] run tellraw @s {"text":"Soulpack debug: nearest backpack assigned to you; v6 should restore it on the next tick.","color":"aqua"}
execute unless entity @e[type=minecraft:item,tag=soulpack_debug_candidate,limit=1] run tellraw @s {"text":"Soulpack debug: no nearby dropped Sophisticated Backpack found.","color":"red"}

tag @e[type=minecraft:item,tag=soulpack_debug_candidate] remove soulpack_debug_candidate
tag @s remove soulpack_debug_owner

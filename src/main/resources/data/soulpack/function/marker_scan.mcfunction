tag @s add soulpack_marker_tmp

# Clear temporary candidates in this marker's scan radius.
tag @e[type=minecraft:item,distance=..8,tag=soulpack_auto_candidate] remove soulpack_auto_candidate

# First identify ALL supported backpack item entities in range.
execute as @e[type=minecraft:item,distance=..8,tag=!soulpack] if items entity @s contents sophisticatedbackpacks:backpack run tag @s add soulpack_auto_candidate
execute as @e[type=minecraft:item,distance=..8,tag=!soulpack] if items entity @s contents sophisticatedbackpacks:copper_backpack run tag @s add soulpack_auto_candidate
execute as @e[type=minecraft:item,distance=..8,tag=!soulpack] if items entity @s contents sophisticatedbackpacks:iron_backpack run tag @s add soulpack_auto_candidate
execute as @e[type=minecraft:item,distance=..8,tag=!soulpack] if items entity @s contents sophisticatedbackpacks:gold_backpack run tag @s add soulpack_auto_candidate
execute as @e[type=minecraft:item,distance=..8,tag=!soulpack] if items entity @s contents sophisticatedbackpacks:diamond_backpack run tag @s add soulpack_auto_candidate
execute as @e[type=minecraft:item,distance=..8,tag=!soulpack] if items entity @s contents sophisticatedbackpacks:netherite_backpack run tag @s add soulpack_auto_candidate

# Only now choose the nearest BACKPACK candidate.
execute if score @s pk_captured matches 0 as @e[type=minecraft:item,distance=..8,tag=soulpack_auto_candidate,sort=nearest,limit=1] run function soulpack:claim_item

# Clean up candidate tags.
tag @e[type=minecraft:item,distance=..8,tag=soulpack_auto_candidate] remove soulpack_auto_candidate
tag @s remove soulpack_marker_tmp

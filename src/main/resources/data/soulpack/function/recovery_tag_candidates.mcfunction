tag @e[type=minecraft:item] remove soulpack_recovery_candidate
execute as @e[type=minecraft:item] if items entity @s contents sophisticatedbackpacks:backpack run tag @s add soulpack_recovery_candidate
execute as @e[type=minecraft:item] if items entity @s contents sophisticatedbackpacks:copper_backpack run tag @s add soulpack_recovery_candidate
execute as @e[type=minecraft:item] if items entity @s contents sophisticatedbackpacks:iron_backpack run tag @s add soulpack_recovery_candidate
execute as @e[type=minecraft:item] if items entity @s contents sophisticatedbackpacks:gold_backpack run tag @s add soulpack_recovery_candidate
execute as @e[type=minecraft:item] if items entity @s contents sophisticatedbackpacks:diamond_backpack run tag @s add soulpack_recovery_candidate
execute as @e[type=minecraft:item] if items entity @s contents sophisticatedbackpacks:netherite_backpack run tag @s add soulpack_recovery_candidate

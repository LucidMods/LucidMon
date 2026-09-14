
data modify entity @s Owner set from entity @a[tag=soulpack_restoring,limit=1] UUID
tp @s @a[tag=soulpack_restoring,limit=1]
data merge entity @s {Age:-32768s,PickupDelay:0s,Invulnerable:1b}
tag @s remove soulpack

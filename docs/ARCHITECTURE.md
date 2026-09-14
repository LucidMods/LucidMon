# LucidMon architecture

LucidMon is being consolidated into one Fabric mod with internally separated modules rather than separate custom JARs/datapacks.

Planned module layout:

```text
LucidMon
├── Core
│   ├── config
│   ├── persistence
│   └── commands
├── MapGen
│   ├── profiles
│   ├── terrain
│   ├── biomes
│   ├── routes
│   ├── landmarks
│   └── BCA compatibility
├── League
│   ├── gyms
│   ├── champion
│   ├── vouchers
│   ├── queue
│   └── qualification
├── MapReveal
├── Soulpack
└── VirtualPasture
```

The current source tree does not yet fully reflect that final package split; refactoring should be incremental to preserve rollback safety.

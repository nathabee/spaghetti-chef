# SpaghettiChef Tools

This directory contains operational helper scripts that are shipped with
SpaghettiChef runtime packages.

The tools are split by target platform:

```text
tools/
├── linux/
│   └── camera/
└── win/
    ├── camera/
    ├── r.ps1
    ├── s.ps1
    ├── t.ps1
    ├── u.ps1
    └── v.ps1
````

## Packaging Rule

Tools are not Java application resources.

They must not be bundled into `spaghetti-chef.jar`.

Windows tools are copied into the Windows package `bin/` directory.

Linux tools are copied into the Linux package tool/script area.

## Runtime Layout

A typical Windows runtime installation uses:

```text
C:\spaghettichef\
├── app\
├── bin\
├── data\
├── log\
├── rel\
└── tmp\
```

Expected meaning:

```text
app\   runtime application files
bin\   operational scripts
data\  persistent database, configuration, camera storage
log\   runtime logs
rel\   downloaded release archives
tmp\   temporary extraction, diagnostics, backups
```

Camera helper scripts belong under `bin\camera\` in the Windows package, not
inside `app\camera\`.

## Platform Documentation

See:

* [`linux/README.md`](linux/README.md)
* [`win/README.md`](win/README.md)

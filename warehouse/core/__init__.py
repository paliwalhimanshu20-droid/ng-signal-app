"""Cross-cutting primitives: constants, exceptions, logging, utils.

Nothing in `warehouse.core` imports from any other `warehouse.*` subpackage —
this package sits at the bottom of the dependency graph so it can be safely
imported from anywhere (storage, metadata, registry, bootstrap, config)
without risk of circular imports.
"""

# Dependency License Review

## Summary

All project dependencies use OSI-approved licenses compatible with Apache-2.0 and Android app distribution.

## Dependencies

| Dependency | License | Compatible |
| --- | --- | --- |
| AndroidX (activity-compose, core-ktx, compose) | Apache-2.0 | ✅ |
| Kotlin stdlib | Apache-2.0 | ✅ |
| Kotlinx Coroutines | Apache-2.0 | ✅ |
| Kotlinx Serialization | Apache-2.0 | ✅ |
| Ktor Server (CIO, core, plugins) | Apache-2.0 | ✅ |
| JUnit 4 | EPL-2.0 (test only) | ✅ |

## Review Date

2024-01-01 (initial review)

## Notes

- All runtime dependencies use Apache-2.0
- JUnit is test-only and does not ship in the APK
- Review should be updated when adding new dependencies

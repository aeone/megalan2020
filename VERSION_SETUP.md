# Version Setup Instructions

## Overview
This project uses `shadow-git-inject` to automatically inject git version information into the application. The version is displayed at the bottom of the page as "Git version: X.X.X".

## Current Status
A local git tag `v1.0.0` has been created but needs to be pushed to the remote repository.

## To Complete Setup

Run the following command to push the tag to the repository:

```bash
git push origin v1.0.0
```

## Future Versioning

To create new versions in the future, follow these steps:

1. Create a new tag with semantic versioning:
   ```bash
   git tag v1.1.0
   ```

2. Push the tag to the repository:
   ```bash
   git push origin v1.1.0
   ```

The version displayed in the application will automatically update based on the most recent tag when the application is built.

## Fallback Behavior

If no tags exist in the repository, the application will display "development build" instead of an error message.

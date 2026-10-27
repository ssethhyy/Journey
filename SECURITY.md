# Security policy

## Supported versions

Journey is a young project. Security fixes are made on the latest release and on `main`.

| Version | Supported |
|---|---|
| Latest release / `main` | ✅ |
| Older releases | ❌ Please update |

## Reporting a vulnerability

**Please do not open a public issue for security problems.**

Use GitHub's private reporting instead: go to the repository's **Security** tab and choose
**Report a vulnerability** (*Security → Advisories → Report a vulnerability*). If that isn't available, contact the
repository owner through their GitHub profile and ask for a private way to share the details.

Please include:

- what the problem is and where it is (file, screen or feature),
- steps to reproduce, and the Android version and device,
- the impact you think it has,
- a suggested fix, if you have one.

You can expect an acknowledgement within about a week and a status update after that. Journey is maintained by
volunteers, so please allow time to investigate, fix and release before sharing the details publicly. We are happy
to credit you in the changelog if you'd like.

## What's in scope

- Anything that could expose a user's **API keys**, location, favourites, myki numbers or trip history.
- Insecure handling of data on the device or on the network.
- Exported components (activities, services, receivers) that can be abused by other apps.
- Problems in the build setup that could leak secrets (for example into logs, artifacts or CI).

Out of scope: vulnerabilities in third-party services (PTV, Transitous, Mapbox, Essentials) or in Android itself.
Please report those to their owners.

## About API keys

Journey is "bring your own keys": the repository contains **none**, and `local.properties` (where you keep yours)
is git-ignored. A few things worth knowing:

- Keys placed in `local.properties` are compiled into the app you build, so **don't share or publish APKs built
  with your own keys**. Anyone with the APK can extract them.
- Use a **public** Mapbox token (`pk.…`), never a secret one (`sk.…`).
- Keys entered in the app under *Settings → API access* are stored privately on the device.

### If you committed a key by accident

Removing it in a later commit is **not enough**, because it stays in Git history.

1. **Revoke or rotate the key immediately** with PTV or Mapbox. Assume it is compromised.
2. Remove it from the code and, if it was pushed, remove it from history (for example with
   [`git filter-repo`](https://github.com/newren/git-filter-repo)) and force-push.
3. Check whether any forks or open pull requests contain it.
4. If the key was in someone else's repository, let them know privately.

To help prevent this, turn on **secret scanning** and **push protection** in your fork's *Settings → Code
security*, and check `git status` and `git diff --staged` before committing.

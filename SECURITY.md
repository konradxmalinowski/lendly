# Security policy

## Reporting a vulnerability

Report vulnerabilities privately with GitHub private vulnerability reporting. Open the repository's **Security** tab and choose **Report a vulnerability**. Please do not open a public issue, pull request, or discussion for a security problem.

Include what you found, the steps or request that reproduce it, the version or commit, and the impact you expect. Do not include real user data. If you tested against a running instance, say which one.

You can expect an acknowledgement of your report, and an update when the issue is confirmed or closed. This is a small project without a fixed response time or a bug bounty.

## Scope

In scope: the code and configuration in this repository, such as authentication and sessions, email verification, multi-factor authentication, CSRF and origin checks, authorization of listings and reservations, image handling, and the handling of AI output.

Out of scope: findings that need a modified or compromised client machine, social engineering, denial of service by sheer traffic, and problems in third-party services such as Google, OpenAI, Gemini, or OpenFreeMap. Report those to the service owner.

## Supported versions

Fixes go to the latest release listed in [CHANGELOG.md](CHANGELOG.md).

## Safe testing

Test only against your own local instance (see [CONTRIBUTING.md](CONTRIBUTING.md)). Do not test against a hosted deployment without the owner's written permission.

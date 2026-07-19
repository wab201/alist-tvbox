# Fork Sync Workflow

This fork keeps upstream code and personal custom changes separated by branch.

## Branch Roles

- `upstream/master`: read-only mirror of `power721/alist-tvbox`.
- `origin/master`: optional mirror of upstream. Do not put long-lived custom work here.
- `codex/*` or `custom/*`: personal feature branches. Keep fork-only behavior here.

The rule is simple: pull upstream into your custom branch, never reset your custom branch to upstream.

## Recommended Sync

From a clean working tree:

```powershell
pwsh -NoLogo -NoProfile -File .\scripts\sync-upstream-preserve-custom.ps1 -Push
```

What the script does:

1. checks that the working tree is clean;
2. fetches `upstream` and `origin`;
3. merges `upstream/master` into the current custom branch with a normal merge commit;
4. leaves your custom commits intact;
5. optionally pushes the result back to your fork when `-Push` is used.

Preview upstream changes without merging:

```powershell
pwsh -NoLogo -NoProfile -File .\scripts\sync-upstream-preserve-custom.ps1 -PreviewOnly
```

Sync a specific branch:

```powershell
pwsh -NoLogo -NoProfile -File .\scripts\sync-upstream-preserve-custom.ps1 -TargetBranch codex/publish-pure-secspider-20260718 -Push
```

## Conflict Rules

When conflicts happen, prefer these rules:

- Keep upstream fixes unless they remove fork-only plugin/compiler behavior.
- Keep fork-owned additions for managed secspider keys, plugin compile/import, compatibility gates, and self-use deployment tooling.
- Do not accept upstream changes by doing `git reset --hard upstream/master`.
- Resolve conflicts once and let Git remember them through `rerere`.

After resolving conflicts:

```powershell
git add <resolved-files>
git merge --continue
git push origin <your-custom-branch>
```

## Local Git Settings

Enable remembered conflict resolutions:

```powershell
git config rerere.enabled true
git config rerere.autoupdate true
```

This makes repeated upstream merges less painful after the same conflict has been resolved once.

## Do Not Mix

Keep fork-only release details out of upstream PRs:

- private image names or GHCR tags;
- NAS addresses, tokens, passwords, cookies, or local paths;
- self-use Docker publish workflows;
- temporary plugin test artifacts.

For upstream PRs, create a fresh branch from `upstream/master` and cherry-pick only generic code, docs, and tests.

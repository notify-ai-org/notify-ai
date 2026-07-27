#!/usr/bin/env bash
set -euo pipefail

usage() {
  printf 'Usage: %s <branch> <commit-message>\n' "$(basename "$0")" >&2
}

if [[ $# -lt 2 ]]; then
  usage
  exit 2
fi

branch="$1"
shift
commit_message="$*"

root_dir="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

discover_repos() {
  if [[ -f "$root_dir/.gitmodules" ]]; then
    git -C "$root_dir" config --file .gitmodules --get-regexp path |
      awk '{print $2}' |
      while IFS= read -r submodule_path; do
        repo="$root_dir/$submodule_path"
        if git -C "$repo" rev-parse --show-toplevel >/dev/null 2>&1; then
          printf '%s\n' "$repo"
        else
          printf 'Skipping %s: submodule is not initialized\n' "$repo" >&2
        fi
      done
  fi

  find "$root_dir" \
    -path "$root_dir/.git" -prune -o \
    -path '*/.git' -print |
    while IFS= read -r git_path; do
      repo="${git_path%/.git}"
      if [[ -d "$repo" ]] && [[ "$repo" != "$root_dir" ]]; then
        printf '%s\n' "$repo"
      fi
    done
}

checkout_branch() {
  local repo="$1"

  if git -C "$repo" show-ref --verify --quiet "refs/heads/$branch"; then
    git -C "$repo" switch "$branch"
    return
  fi

  if git -C "$repo" ls-remote --exit-code --heads origin "$branch" >/dev/null 2>&1; then
    git -C "$repo" switch --track "origin/$branch"
    return
  fi

  git -C "$repo" switch -c "$branch"
}

commit_if_needed() {
  local repo="$1"

  git -C "$repo" add -A
  if git -C "$repo" diff --cached --quiet; then
    printf 'No changes to commit in %s\n' "$repo"
    return
  fi

  git -C "$repo" commit -m "$commit_message"
}

push_branch() {
  local repo="$1"

  if ! git -C "$repo" remote get-url origin >/dev/null 2>&1; then
    printf 'Skipping push for %s: no origin remote\n' "$repo" >&2
    return
  fi

  git -C "$repo" push -u origin "$branch"
}

mapfile -t sub_repos < <(discover_repos | sort -u)

printf '==> %s\n' "$root_dir"
checkout_branch "$root_dir"

for repo in "${sub_repos[@]}"; do
  printf '\n==> %s\n' "$repo"
  checkout_branch "$repo"
done

for repo in "${sub_repos[@]}"; do
  printf '\n==> %s\n' "$repo"
  commit_if_needed "$repo"
  push_branch "$repo"
done

printf '\n==> %s\n' "$root_dir"
commit_if_needed "$root_dir"
push_branch "$root_dir"

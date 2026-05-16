#!/bin/bash
set -e

TOKEN="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"
USER="rohan-naik07"
MODULES=("access" "api" "acp-server" "annotations" "client" "common" "engine" "examples" "notify-ui")
BASE_DIR="/Users/rohannaik/Desktop/notify"
TMP_DIR="/tmp/notify-modules-migration"

rm -rf $TMP_DIR
mkdir -p $TMP_DIR

cd $BASE_DIR

for MODULE in "${MODULES[@]}"; do
  echo "======================================"
  echo "Processing $MODULE..."
  
  # 1. Create remote repo via GitHub API
  HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST -H "Authorization: token $TOKEN" \
       -H "Accept: application/vnd.github.v3+json" \
       -d "{\"name\":\"$MODULE\", \"private\": true}" \
       https://api.github.com/user/repos)
  
  if [ "$HTTP_STATUS" != "201" ] && [ "$HTTP_STATUS" != "422" ]; then
    echo "Failed to create repository for $MODULE. HTTP Status: $HTTP_STATUS"
    exit 1
  fi
  
  echo "Repo $MODULE created or already exists."

  # 2. Copy the module to a temporary directory
  cp -R "$BASE_DIR/$MODULE" "$TMP_DIR/$MODULE"
  
  # Add broad gitignore to avoid large dependency trees
  cat << 'EOF' > "$TMP_DIR/$MODULE/.gitignore"
**/target/
**/node_modules/
**/dist/
*.iml
.idea/
.DS_Store
application-local.properties
minikube-secrets-template.yaml
EOF

  cd "$TMP_DIR/$MODULE"
  
  # If there was a .git folder somehow, remove it
  rm -rf .git
  
  # 3. Init, commit, and push
  git init
  git add .
  git commit -m "Initial commit for $MODULE module"
  git branch -M main
  git remote add origin "https://${USER}:${TOKEN}@github.com/${USER}/${MODULE}.git"
  git push -u origin main --force
  
  # 4. Remove from base repo
  cd "$BASE_DIR"
  git rm -r --cached "$MODULE" > /dev/null || true
  rm -rf "$MODULE"
  
  # 5. Add as submodule
  # We use the https remote without the token for the .gitmodules so the token isn't stored in plain text!
  git submodule add "https://github.com/${USER}/${MODULE}.git" "$MODULE"
  
  echo "Finished $MODULE"
done

echo "======================================"
echo "All modules processed. Committing submodules to base repo..."
git commit -m "Convert 9 modules to git submodules"
echo "Done!"

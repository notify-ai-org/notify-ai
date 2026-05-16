#!/bin/bash
set -e

TOKEN="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"
USER="rohan-naik07"
MODULES=("examples" "notify-ui")
BASE_DIR="/Users/rohannaik/Desktop/notify"
TMP_DIR="/tmp/notify-modules-migration"

mkdir -p $TMP_DIR

cd $BASE_DIR

for MODULE in "${MODULES[@]}"; do
  echo "======================================"
  echo "Processing $MODULE..."
  
  HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST -H "Authorization: token $TOKEN" \
       -H "Accept: application/vnd.github.v3+json" \
       -d "{\"name\":\"$MODULE\", \"private\": true}" \
       https://api.github.com/user/repos)
  
  if [ "$HTTP_STATUS" != "201" ] && [ "$HTTP_STATUS" != "422" ]; then
    echo "Failed to create repository for $MODULE. HTTP Status: $HTTP_STATUS"
    exit 1
  fi
  
  cp -R "$BASE_DIR/$MODULE" "$TMP_DIR/$MODULE"
  
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
  rm -rf .git
  
  git init
  git add .
  git commit -m "Initial commit for $MODULE module"
  git branch -M main
  git remote add origin "https://${USER}:${TOKEN}@github.com/${USER}/${MODULE}.git"
  git push -u origin main --force
  
  cd "$BASE_DIR"
  git rm -r --cached "$MODULE" > /dev/null || true
  rm -rf "$MODULE"
  
  git submodule add "https://github.com/${USER}/${MODULE}.git" "$MODULE"
  
  echo "Finished $MODULE"
done

echo "Done!"

#!/data/data/com.termux/files/usr/bin/bash
# ============================================================================
#  LastWave — Android TV Code Uploader (Termux Script)
# ============================================================================
#  Ye script LastWave ke TV support wale code (17 files) ko extract karke
#  tumhare GitHub repo mein push kar deti hai. Sab kuch automatic:
#    1. Code /storage/emulated/0/xxxtermuxxd/lastwave se lega
#       (agar wahan code nahi mila to zip khud download karke extract karega)
#    2. Tumhare repo ka fresh clone banayega
#    3. TV code ko uske upar overlay karega
#    4. Commit karke push kar dega
#
#  SETUP (sirf ek baar):
#    1. Neeche GITHUB_TOKEN="" line mein apna GitHub token paste karo
#       (token mein "repo"/Contents write permission honi chahiye)
#       Token yahan se banao: https://github.com/settings/tokens
#    2. Termux kholo:
#         chmod +x lastwave-tv-upload.sh
#         ./lastwave-tv-upload.sh
#
#  Baaki sab settings bhi neeche CONFIG section mein hain.
# ============================================================================

# ------------------------------ CONFIG --------------------------------------
# <<== YAHAN APNA GITHUB TOKEN DALO (github.com/settings/tokens) ==>>
GITHUB_TOKEN=""

# Jis repo mein push karna hai (owner/repo format):
REPO="DominatorStufs/LastWave-Native"

# Kis branch mein push karna hai:
BRANCH="main"

# Jahan tumne zip extract kiya hai (ya extract hoga):
SOURCE_DIR="/storage/emulated/0/xxxtermuxxd/lastwave"

# TV code zip (agar folder khali mile to script khud yahan se download karega):
ZIP_URL="https://github.com/DominatorPVT/LastWave-Native/raw/arena/ec9626f2-lastwave-native/LastWave-TV-files.zip"

# Commit ki identity (khali chhoda to default use hoga):
GIT_NAME=""
GIT_EMAIL=""

# 1 karo to purani history replace karke force push hoga (DHYAAN SE!):
FORCE_PUSH=0

# Push ke baad signed TV APK build auto-trigger karna (1=haan, 0=haath se):
TRIGGER_BUILD=1
# -----------------------------------------------------------------------------


set -u
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
say()  { printf "${CYAN}==>${NC} %s\n" "$1"; }
ok()   { printf "${GREEN} ✔${NC} %s\n" "$1"; }
warn() { printf "${YELLOW} !${NC} %s\n" "$1"; }
die()  { printf "${RED} ✘ %s${NC}\n" "$1" >&2; exit 1; }
TMPDIR_WORK=""

cleanup() { [ -n "$TMPDIR_WORK" ] && rm -rf "$TMPDIR_WORK"; }
trap cleanup EXIT INT TERM

# ---- 1. Token ---------------------------------------------------------------
if [ -z "$GITHUB_TOKEN" ]; then
    if [ -n "${GITHUB_TOKEN_ENV:-}" ]; then
        GITHUB_TOKEN="$GITHUB_TOKEN_ENV"
    else
        warn "GITHUB_TOKEN config mein khali hai — manually pooch raha hu."
        printf "GitHub token paste karo (input chhupa rahe honge): "
        read -r -s GITHUB_TOKEN
        echo ""
    fi
fi
[ -n "$GITHUB_TOKEN" ] || die "GitHub token nahi mila. Script kholo aur GITHUB_TOKEN=\"\" mein token daalo."

# ---- 2. Dependencies --------------------------------------------------------
say "Dependencies check kar raha hu..."
MISSING=""
command -v git   >/dev/null 2>&1 || MISSING="$MISSING git"
command -v curl  >/dev/null 2>&1 || MISSING="$MISSING curl"
command -v unzip >/dev/null 2>&1 || MISSING="$MISSING unzip"
if [ -n "$MISSING" ]; then
    warn "Ye packages missing hain: $MISSING — install kar raha hu..."
    pkg install -y $MISSING || die "pkg install fail hua. Manually chalao: pkg install $MISSING"
fi
ok "git, curl, unzip sab ready"

# ---- 3. Token verify + push permission --------------------------------------
say "Token verify kar raha hu (repo: $REPO)..."
REPO_API=$(curl -s -H "Authorization: token $GITHUB_TOKEN" "https://api.github.com/repos/$REPO") \
    || die "GitHub API tak nahi pahunch paaya — internet check karo."
echo "$REPO_API" | grep -q '"full_name"' || die "Repo $REPO nahi mila ya token galat hai. API reply: $(echo "$REPO_API" | head -c 300)"
PUSH_OK=$(echo "$REPO_API" | grep -A6 '"permissions"' | grep '"push"' | grep -c 'true')
[ "$PUSH_OK" = "1" ] || die "Is token ko $REPO mein PUSH ki permission nahi hai. Token banate waqt 'repo' scope (ya fine-grained mein Contents: Read & Write) do."
ok "Token sahi hai, push permission hai"

# ---- 4. Storage access (Termux) --------------------------------------------
if [ -d "/storage/emulated/0" ] && [ ! -w "/storage/emulated/0" ]; then
    warn "/storage/emulated/0 accessible nahi hai."
    say "termux-setup-storage chalao (permission popup aayega, ALLOW karo), phir script dobara chalao."
    termux-setup-storage 2>/dev/null || true
    die "Storage permission ke baad script dobara run karo."
fi

# ---- 5. Source code nikalo --------------------------------------------------
say "TV code dhoond raha hu: $SOURCE_DIR"

resolve_source() {
    # (a) Direct folder
    if [ -f "$SOURCE_DIR/app/build.gradle.kts" ]; then
        SRC="$SOURCE_DIR"; return 0
    fi
    # (b) Extract hone ke baad ek andar wale folder mein ho sakta hai
    for sub in "$SOURCE_DIR"/*/; do
        [ -f "$sub/app/build.gradle.kts" ] && SRC="${sub%/}" && return 0
    done
    return 1
}

# Pehle GitHub se FRESH zip try karo — taaki purane extract kiye folder pe
# depend na karna pade (main zip update karta rahunga, script same rahegi).
mkdir -p "$SOURCE_DIR" || die "Folder nahi bana paaya: $SOURCE_DIR"
FRESH_DIR="$SOURCE_DIR/.fresh-tv-code"
rm -rf "$FRESH_DIR"; mkdir -p "$FRESH_DIR"
ZIP_PATH="$FRESH_DIR/LastWave-TV-files.zip"
say "Fresh zip download kar raha hu (GitHub se latest)..."
if curl -fL --retry 3 -o "$ZIP_PATH" "$ZIP_URL"; then
    ZIP_BYTES=$(wc -c < "$ZIP_PATH")
    if [ "$ZIP_BYTES" -ge 100000 ]; then
        ok "Fresh zip mil gayi ($(( ZIP_BYTES / 1024 )) KB) — extract kar raha hu..."
        unzip -oq "$ZIP_PATH" -d "$FRESH_DIR" || die "Unzip fail hua."
        rm -f "$ZIP_PATH"
        SRC="$FRESH_DIR"
    else
        warn "Downloaded zip chhoti thi ($ZIP_BYTES bytes) — local folder pe fallback."
        SRC=""
    fi
else
    warn "Download fail hua — local extracted folder pe fallback."
    SRC=""
fi

# Fallback: local folder (direct ya ek level andar)
if [ -z "$SRC" ]; then
    if resolve_source; then
        ok "Local code use kar raha hu: $SRC"
    else
        die "Na fresh zip mili na valid local code. Internet check karo ya $SOURCE_DIR mein zip manually extract karo."
    fi
fi

# ---- 6. Repo clone ----------------------------------------------------------
say "Repo clone kar raha hu: github.com/$REPO (branch: $BRANCH)..."
TMPDIR_WORK=$(mktemp -d) || die "Temp folder nahi ban paaya."
WORK="$TMPDIR_WORK/repo"
git clone --depth=1 "https://github.com/$REPO.git" "$WORK" -b "$BRANCH" \
    || die "Clone fail hua. Repo/branch naam check karo ($REPO@$BRANCH)."
OLD_SHA=$(git -C "$WORK" rev-parse HEAD)
ok "Clone ho gaya (pehle wala commit: ${OLD_SHA:0:7})"

# ---- 7. Overlay -------------------------------------------------------------
say "TV code repo ke upar copy kar raha hu..."
cp -r "$SRC/." "$WORK/"
# Upload tool ki khud ki files repo mein nahi jaani chahiye:
rm -f "$WORK/LASTWAVE-TV-APPLY-KRO.txt" "$WORK/lastwave-tv-upload.sh"
find "$WORK" -maxdepth 1 -name "*.zip" -delete
[ -f "$WORK/app/src/main/java/com/lastwave/app/ui/tv/TvHomeScreen.kt" ] || die "TV files copy nahi hui — source folder dobara check karo."
ok "Overlay poora hua (17 files: TV UI, manifest, gradle, workflow, strings, icons)"

# ---- 8. Commit --------------------------------------------------------------
git -C "$WORK" config user.name  "${GIT_NAME:-LastWave TV}"
git -C "$WORK" config user.email "${GIT_EMAIL:-tv@lastwave.local}"
git -C "$WORK" add -A
if git -C "$WORK" diff --cached --quiet; then
    ok "Kuch naya nahi tha — repo mein pehle se sab TV code hai. Ho gaya!"
    exit 0
fi
CHANGED=$(git -C "$WORK" diff --cached --numstat | wc -l)
git -C "$WORK" commit -q -m "feat(tv): Android TV support — D-pad home, leanback launcher, com.lastwave.app.tv build

- Leanback launcher activity with banner + TV icon
- D-pad home: now-playing hero (transport + +-10s seek), top tracks,
  recently played, remote-keyboard search
- Same MusicPlayer singleton — media session/queue/radio identical to phone
- Phone launcher untouched (leanback feature required=false)
- Universal APK; TV build ships as package com.lastwave.app.tv (-PtvApp=true)
- Build workflow: 'TV build' input toggle for signed TV APKs"
ok "Commit ban gaya ($CHANGED files changed)"

# ---- 9. Push ----------------------------------------------------------------
say "Push kar raha hu github.com/$REPO@$BRANCH ..."
git -C "$WORK" remote set-url origin "https://x-access-token:${GITHUB_TOKEN}@github.com/${REPO}.git"
PUSH_ARGS=""
[ "$FORCE_PUSH" = "1" ] && PUSH_ARGS="--force"
if git -C "$WORK" push $PUSH_ARGS origin "$BRANCH" 2>"$TMPDIR_WORK/push.log"; then
    NEW_SHA=$(git -C "$WORK" rev-parse HEAD)
    echo ""
    printf "${GREEN}=====================================================${NC}\n"
    printf "${GREEN}  HO GAYA! TV code GitHub pe push ho gaya 🎉${NC}\n"
    printf "${GREEN}=====================================================${NC}\n"
    echo "  Repo    : https://github.com/$REPO"
    echo "  Branch  : $BRANCH"
    echo "  Commit  : https://github.com/$REPO/commit/${NEW_SHA:0:7}"
    echo "  Pehle   : $OLD_SHA"
    echo ""
    echo "  Next: GitHub -> Actions -> Build LastWave APK -> Run workflow"
    echo "        -> 'TV build' tick karo -> signed TV APK artifact milega"
    echo "        (package: com.lastwave.app.tv, universal)"
    echo ""
else
    warn "Push reject hua. Log:"
    cat "$TMPDIR_WORK/push.log"
    echo ""
    die "Fix: remote branch aage nikal chuki hai. FORCE_PUSH=1 ke saath chalao:
         FORCE_PUSH=1 ./lastwave-tv-upload.sh   (DHYAAN SE — purani main overwrite hogi)
         Ya pehle normal push kara ke dekho."
fi

# ---- 10. TV build trigger (agar repo pe workflow hai) ------------------------
if [ "$TRIGGER_BUILD" = "1" ]; then
    say "Signed TV APK build trigger kar raha hu (Actions workflow)..."
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
        -H "Authorization: token $GITHUB_TOKEN" \
        -H "Accept: application/vnd.github+json" \
        "https://api.github.com/repos/$REPO/actions/workflows/build.yml/dispatches" \
        -d "{\"ref\":\"$BRANCH\",\"inputs\":{\"tv_build\":true}}")
    if [ "$HTTP_CODE" = "204" ]; then
        ok "Build queue ho gaya!"
        echo ""
        printf "${GREEN}  Signed TV APK yahan milega (2-3 min baad artifact):${NC}\n"
        echo "  https://github.com/$REPO/actions"
        echo ""
        echo "  Package: com.lastwave.app.tv (phone app ke saath saath install rahega)"
    else
        warn "Auto-trigger nahi hua (HTTP $HTTP_CODE — token mein actions:write chahiye)."
        echo "  Manual: https://github.com/$REPO/actions -> Build LastWave APK -> Run workflow -> 'TV build' tick"
    fi
fi

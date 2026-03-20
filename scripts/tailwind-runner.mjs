import { createHash } from "node:crypto";
import { spawnSync } from "node:child_process";
import {
  closeSync,
  existsSync,
  openSync,
  readFileSync,
  statSync,
  unlinkSync,
  writeFileSync,
} from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const projectRoot = join(scriptDir, "..");
const nodeModulesDir = join(projectRoot, "node_modules");
const packageLockPath = join(projectRoot, "package-lock.json");
const tailwindCliPath = join(
  nodeModulesDir,
  "@tailwindcss",
  "cli",
  "dist",
  "index.mjs",
);
const statePath = join(nodeModulesDir, ".tailwind-runner-state.json");
const lockPath = join(projectRoot, ".tailwind-runner.lock");
const lockTimeoutMs = 180000;
const staleLockMs = 600000;

const args = process.argv.slice(2);
const ensureOnly = args.includes("--ensure-only");
const watchMode = args.includes("--watch");
const minifyMode = args.includes("--minify");

if (watchMode && minifyMode) {
  console.error("Cannot run with both --watch and --minify.");
  process.exit(2);
}

function sleep(ms) {
  const buffer = new SharedArrayBuffer(4);
  const int32 = new Int32Array(buffer);
  Atomics.wait(int32, 0, 0, ms);
}

function run(command, commandArgs) {
  const result = spawnSync(command, commandArgs, {
    cwd: projectRoot,
    stdio: "inherit",
  });

  if (result.error) {
    console.error(result.error.message);
    process.exit(1);
  }

  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
}

function lockFileHash() {
  if (!existsSync(packageLockPath)) {
    return "";
  }

  return createHash("sha256").update(readFileSync(packageLockPath)).digest("hex");
}

function loadState() {
  if (!existsSync(statePath)) {
    return null;
  }

  try {
    return JSON.parse(readFileSync(statePath, "utf8"));
  } catch {
    return null;
  }
}

function writeState(lockHash) {
  writeFileSync(
    statePath,
    JSON.stringify(
      {
        lockHash,
        platform: process.platform,
        arch: process.arch,
      },
      null,
      2,
    ),
    "utf8",
  );
}

function needsInstall(lockHash) {
  if (!existsSync(tailwindCliPath)) {
    return true;
  }

  const state = loadState();
  if (!state) {
    return true;
  }

  return (
    state.lockHash !== lockHash ||
    state.platform !== process.platform ||
    state.arch !== process.arch
  );
}

function clearStaleLock() {
  if (!existsSync(lockPath)) {
    return;
  }

  try {
    const ageMs = Date.now() - statSync(lockPath).mtimeMs;
    if (ageMs > staleLockMs) {
      unlinkSync(lockPath);
    }
  } catch {
    try {
      unlinkSync(lockPath);
    } catch {
      // ignore
    }
  }
}

function acquireLock() {
  const startedAt = Date.now();

  while (Date.now() - startedAt < lockTimeoutMs) {
    clearStaleLock();
    try {
      return openSync(lockPath, "wx");
    } catch (error) {
      if (error.code !== "EEXIST") {
        throw error;
      }
      sleep(250);
    }
  }

  throw new Error(`Timed out waiting for ${lockPath}`);
}

function releaseLock(lockFd) {
  try {
    closeSync(lockFd);
  } finally {
    try {
      unlinkSync(lockPath);
    } catch {
      // ignore
    }
  }
}

function ensureDependencies() {
  const currentHash = lockFileHash();
  if (!needsInstall(currentHash)) {
    return;
  }

  const lockFd = acquireLock();
  try {
    const hashAfterLock = lockFileHash();
    if (!needsInstall(hashAfterLock)) {
      return;
    }

    if (process.platform === "win32") {
      run("cmd.exe", ["/d", "/s", "/c", "npm ci --no-audit --no-fund"]);
    } else {
      run("npm", ["ci", "--no-audit", "--no-fund"]);
    }

    writeState(hashAfterLock);
  } finally {
    releaseLock(lockFd);
  }
}

function runTailwind() {
  const commandArgs = [
    tailwindCliPath,
    "-i",
    "web/src/main/resources/static/input.css",
    "-o",
    "web/src/main/resources/static/site.css",
  ];

  if (watchMode) {
    commandArgs.push("--watch");
  } else if (minifyMode || !watchMode) {
    commandArgs.push("--minify");
  }

  run(process.execPath, commandArgs);
}

ensureDependencies();
if (!ensureOnly) {
  runTailwind();
}

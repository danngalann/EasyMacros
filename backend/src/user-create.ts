import { createInterface } from "node:readline/promises";

import { AuthService } from "./auth.js";
import { loadConfig } from "./config.js";
import { createDatabase } from "./database.js";

const readline = createInterface({ input: process.stdin, output: process.stdout, terminal: true });
const username = await readline.question("Username: ");
process.stdout.write("Password: ");
process.stdin.setRawMode?.(true);
let password = "";

await new Promise<void>((resolve) => {
  process.stdin.on("data", (chunk: Buffer) => {
    const character = chunk.toString("utf8");
    if (character === "\r" || character === "\n") {
      process.stdin.setRawMode?.(false);
      process.stdout.write("\n");
      resolve();
    } else if (character === "\u0003") {
      process.exitCode = 130;
      resolve();
    } else if (character === "\u007f") {
      password = password.slice(0, -1);
    } else {
      password += character;
    }
  });
});
readline.close();

if (!process.exitCode) {
  const config = loadConfig();
  const database = createDatabase(config.databaseUrl);
  try {
    const user = await new AuthService(database, config).createUser(username, password);
    console.log(`Created user ${user.username}.`);
  } finally {
    await database.close();
  }
}

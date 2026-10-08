import { supervise } from './launch.mts';
// Prepared only. This starts the coding agent; requires task-specific coding authorization and explicit configuration.
process.exitCode = await supervise('business-worker.mts');

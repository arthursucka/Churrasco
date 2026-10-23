// MongoDB descartável só para o teste local. Replica set porque a aprovação
// de contas antigas usa transações.
const { MongoMemoryReplSet } = require('mongodb-memory-server');
(async () => {
  const set = await MongoMemoryReplSet.create({
    replSet: { count: 1, storageEngine: 'wiredTiger' },
    instanceOpts: [{ ip: '127.0.0.1', port: 27027 }],
  });
  console.log('MONGO_READY', set.getUri());
  setInterval(() => {}, 1 << 30);
})();

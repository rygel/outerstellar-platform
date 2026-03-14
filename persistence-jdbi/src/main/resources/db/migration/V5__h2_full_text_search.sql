-- H2 supports a simple built-in full text search.
-- We initialize it if it's not already there.
-- This requires the H2 FullText class to be available in the classpath.
CREATE ALIAS IF NOT EXISTS FT_INIT FOR "org.h2.fulltext.FullText.init";
CALL FT_INIT();

-- We can create a full text index for the messages table.
-- It will index all columns by default, but we can specify them.
CREATE ALIAS IF NOT EXISTS FT_CREATE_INDEX FOR "org.h2.fulltext.FullText.createIndex";
CALL FT_CREATE_INDEX('PUBLIC', 'MESSAGES', 'AUTHOR,CONTENT');

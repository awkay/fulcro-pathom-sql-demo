# SQL Exploration for Fulcro

This is a sample project where we explore some various ways of coping
with SQL when are using Datomic pull style queries, as is done in Fulcro and Om Next.

It uses H2 for the database, so there is no additional software to install.

See `src/test` for the examples.

To run the tests so you can play with them:

1. Start a REPL
2. Run `(start-server-tests)`
3. Browse to http://localhost:8888/fulcro-spec-server-tests.html

OR

```bash
$ lein test-refresh
```

and the results will display in the terminal.


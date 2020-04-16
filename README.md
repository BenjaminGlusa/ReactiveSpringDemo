1. Start Databases by running `docker-compose up -d`
2. Make sure databases are up and running: `docker ps` & `docker logs`
3. Log into postgres: `docker exec -it postges /bin/bash` and `psql dogs ben` and `SELECT * FROM dog;`
4. Log into mongogb: `docker exec -it mongo /bin/bash` and `mongo` and `use test` and `db.test.find()`
5. Open project in IntelliJ Idea

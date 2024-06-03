delete from flyway_schema_history where version = '1';
--Step 1: Remove Duplicates First, identify any duplicate category names:
SELECT name, COUNT(*)
FROM category
GROUP BY name
HAVING COUNT(*) > 1;

--Then, remove duplicates while keeping one instance of each:
DELETE FROM category
WHERE id NOT IN (
    SELECT MIN(id)
    FROM category
    GROUP BY name
    );


-- Step 2: Add the Unique Constraint
-- After ensuring no duplicates, you can safely add the unique constraint:

ALTER TABLE category
    ADD CONSTRAINT unique_name UNIQUE (name);

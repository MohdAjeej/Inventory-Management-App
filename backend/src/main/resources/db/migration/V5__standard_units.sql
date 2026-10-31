-- Standard units of measure shared by every business, grouped by what they measure.
-- A unit with business_id NULL is a standard unit; a unit with a business is that business's custom unit.
-- base_factor: how many of the category's base unit (PCS, KG, M, M², L, SEC) one of this unit equals.
-- NULL means the unit has no fixed size (a box, a bag, a month) and converts only through explicit conversions.

ALTER TABLE units ALTER COLUMN business_id DROP NOT NULL;
ALTER TABLE units ADD COLUMN category VARCHAR(16) NOT NULL DEFAULT 'OTHER'
    CHECK (category IN ('COUNT', 'WEIGHT', 'LENGTH', 'AREA', 'VOLUME', 'TIME', 'OTHER'));
ALTER TABLE units ADD COLUMN base_factor NUMERIC(30,15) CHECK (base_factor > 0);

CREATE UNIQUE INDEX uk_units_standard_code ON units (lower(code)) WHERE business_id IS NULL;
CREATE UNIQUE INDEX uk_units_standard_name ON units (lower(name)) WHERE business_id IS NULL;
CREATE INDEX idx_units_category ON units (category);

INSERT INTO units (business_id, code, name, decimals, category, base_factor) VALUES
    -- Quantity / count
    (NULL, 'PCS', 'Piece', 0, 'COUNT', 1),
    (NULL, 'UNT', 'Unit', 0, 'COUNT', 1),
    (NULL, 'NOS', 'Number', 0, 'COUNT', 1),
    (NULL, 'DOZ', 'Dozen', 2, 'COUNT', 12),
    (NULL, 'PRS', 'Pair', 0, 'COUNT', 2),
    (NULL, 'SET', 'Set', 0, 'COUNT', NULL),
    (NULL, 'PAC', 'Pack', 0, 'COUNT', NULL),
    (NULL, 'BDL', 'Bundle', 0, 'COUNT', NULL),
    (NULL, 'BOX', 'Box', 0, 'COUNT', NULL),
    (NULL, 'CTN', 'Carton', 0, 'COUNT', NULL),
    (NULL, 'CAS', 'Case', 0, 'COUNT', NULL),
    (NULL, 'ROL', 'Roll', 0, 'COUNT', NULL),
    (NULL, 'SHT', 'Sheet', 0, 'COUNT', NULL),
    (NULL, 'KIT', 'Kit', 0, 'COUNT', NULL),
    (NULL, 'BAG', 'Bag', 0, 'COUNT', NULL),
    (NULL, 'BTL', 'Bottle', 0, 'COUNT', NULL),
    (NULL, 'CAN', 'Can', 0, 'COUNT', NULL),
    (NULL, 'DRM', 'Drum', 0, 'COUNT', NULL),
    -- Weight (base: kilogram)
    (NULL, 'KG', 'Kilogram', 3, 'WEIGHT', 1),
    (NULL, 'G', 'Gram', 0, 'WEIGHT', 0.001),
    (NULL, 'MG', 'Milligram', 0, 'WEIGHT', 0.000001),
    (NULL, 'MT', 'Metric Ton', 3, 'WEIGHT', 1000),
    (NULL, 'QTL', 'Quintal', 2, 'WEIGHT', 100),
    (NULL, 'LB', 'Pound', 3, 'WEIGHT', 0.45359237),
    (NULL, 'OZ', 'Ounce', 2, 'WEIGHT', 0.028349523125),
    -- Length (base: metre)
    (NULL, 'MM', 'Millimetre', 0, 'LENGTH', 0.001),
    (NULL, 'CM', 'Centimetre', 1, 'LENGTH', 0.01),
    (NULL, 'M', 'Metre', 2, 'LENGTH', 1),
    (NULL, 'KM', 'Kilometre', 3, 'LENGTH', 1000),
    (NULL, 'IN', 'Inch', 2, 'LENGTH', 0.0254),
    (NULL, 'FT', 'Foot', 2, 'LENGTH', 0.3048),
    (NULL, 'YD', 'Yard', 2, 'LENGTH', 0.9144),
    -- Area (base: square metre)
    (NULL, 'MM²', 'Square Millimetre', 0, 'AREA', 0.000001),
    (NULL, 'CM²', 'Square Centimetre', 1, 'AREA', 0.0001),
    (NULL, 'M²', 'Square Metre', 2, 'AREA', 1),
    (NULL, 'SQ FT', 'Square Foot', 2, 'AREA', 0.09290304),
    (NULL, 'SQ YD', 'Square Yard', 2, 'AREA', 0.83612736),
    (NULL, 'AC', 'Acre', 3, 'AREA', 4046.8564224),
    (NULL, 'HA', 'Hectare', 3, 'AREA', 10000),
    -- Volume (base: litre)
    (NULL, 'ML', 'Millilitre', 0, 'VOLUME', 0.001),
    (NULL, 'L', 'Litre', 3, 'VOLUME', 1),
    (NULL, 'KL', 'Kilolitre', 3, 'VOLUME', 1000),
    (NULL, 'CM³', 'Cubic Centimetre', 0, 'VOLUME', 0.001),
    (NULL, 'M³', 'Cubic Metre', 3, 'VOLUME', 1000),
    (NULL, 'CU FT', 'Cubic Foot', 3, 'VOLUME', 28.316846592),
    (NULL, 'CU IN', 'Cubic Inch', 2, 'VOLUME', 0.016387064),
    -- Time (base: second); months and years vary in length, so they have no fixed factor
    (NULL, 'SEC', 'Second', 0, 'TIME', 1),
    (NULL, 'MIN', 'Minute', 0, 'TIME', 60),
    (NULL, 'HR', 'Hour', 2, 'TIME', 3600),
    (NULL, 'DAY', 'Day', 1, 'TIME', 86400),
    (NULL, 'WK', 'Week', 1, 'TIME', 604800),
    (NULL, 'MON', 'Month', 0, 'TIME', NULL),
    (NULL, 'YR', 'Year', 0, 'TIME', NULL);

-- ---------------------------------------------------------------- merge the per-business copies

-- Units copied into each business at sign-up become the matching standard unit. A unit whose decimal places were
-- changed stays a custom unit of that business, so no product's quantity precision changes.
CREATE TEMPORARY TABLE unit_merge ON COMMIT DROP AS
SELECT u.id AS old_id, s.id AS new_id
FROM units u
JOIN units s ON s.business_id IS NULL
    AND lower(s.code) = CASE lower(u.code) WHEN 'pack' THEN 'pac' WHEN 'sqm' THEN 'm²' ELSE lower(u.code) END
    AND s.decimals = u.decimals
WHERE u.business_id IS NOT NULL;

CREATE TEMPORARY TABLE conversion_merge ON COMMIT DROP AS
SELECT c.id, c.business_id, c.product_id,
       COALESCE(mf.new_id, c.from_unit_id) AS from_id,
       COALESCE(mt.new_id, c.to_unit_id) AS to_id
FROM unit_conversions c
LEFT JOIN unit_merge mf ON mf.old_id = c.from_unit_id
LEFT JOIN unit_merge mt ON mt.old_id = c.to_unit_id;

-- Conversions that would point a unit at itself.
DELETE FROM unit_conversions c USING conversion_merge n WHERE c.id = n.id AND n.from_id = n.to_id;

-- General conversions between two standard units of one category are now built in (1 KG = 1000 G).
DELETE FROM unit_conversions c USING conversion_merge n, units uf, units ut
WHERE c.id = n.id AND n.product_id IS NULL AND uf.id = n.from_id AND ut.id = n.to_id
  AND uf.base_factor IS NOT NULL AND ut.base_factor IS NOT NULL AND uf.category = ut.category;

-- Conversions that would duplicate another one: keep the oldest.
DELETE FROM unit_conversions c USING conversion_merge n
WHERE c.id = n.id AND EXISTS (
    SELECT 1 FROM conversion_merge o JOIN unit_conversions oc ON oc.id = o.id
    WHERE o.business_id = n.business_id AND o.from_id = n.from_id AND o.to_id = n.to_id
      AND COALESCE(o.product_id, 0) = COALESCE(n.product_id, 0) AND o.id < n.id);

UPDATE unit_conversions c SET from_unit_id = n.from_id, to_unit_id = n.to_id
FROM conversion_merge n
WHERE c.id = n.id AND (c.from_unit_id <> n.from_id OR c.to_unit_id <> n.to_id);

UPDATE products p SET base_unit_id = m.new_id FROM unit_merge m WHERE p.base_unit_id = m.old_id;

DELETE FROM units WHERE id IN (SELECT old_id FROM unit_merge);

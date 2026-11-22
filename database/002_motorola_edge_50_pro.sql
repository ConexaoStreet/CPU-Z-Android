-- Especificações de fábrica, não identificação de peças originais.
-- Fonte: https://www.motorola.com.br/smartphone-motorola-edge-50-pro/p
INSERT INTO public.hardware_catalog
(manufacturer,model,component_category,expected_attributes,official_source_url,source_publisher,published,verified_at)
VALUES
('Motorola','edge 50 pro','processador','{"soc":"Qualcomm Snapdragon 7 Gen 3","cpu_cores":8,"clock_max_ghz":2.6,"gpu":"Adreno 720"}','https://www.motorola.com.br/smartphone-motorola-edge-50-pro/p','Motorola Brasil',true,now()),
('Motorola','edge 50 pro','tela','{"technology":"pOLED","diagonal_inches":6.7,"resolution_px":"1220x2712","refresh_max_hz":144}','https://www.motorola.com.br/smartphone-motorola-edge-50-pro/p','Motorola Brasil',true,now()),
('Motorola','edge 50 pro','bateria','{"design_capacity_mah":4500}','https://www.motorola.com.br/smartphone-motorola-edge-50-pro/p','Motorola Brasil',true,now()),
('Motorola','edge 50 pro','carregamento','{"wired_max_w":125,"wireless_max_w":50}','https://www.motorola.com.br/smartphone-motorola-edge-50-pro/p','Motorola Brasil',true,now()),
('Motorola','edge 50 pro','cameras','{"rear_main_mp":50,"rear_ultrawide_mp":13,"rear_telephoto_mp":10,"front_mp":50}','https://www.motorola.com.br/smartphone-motorola-edge-50-pro/p','Motorola Brasil',true,now()),
('Motorola','edge 50 pro','memoria_armazenamento','{"ram_gb":12,"storage_gb":256}','https://www.motorola.com.br/smartphone-motorola-edge-50-pro/p','Motorola Brasil',true,now())
ON CONFLICT(manufacturer,model,component_category) DO UPDATE SET
expected_attributes=EXCLUDED.expected_attributes,official_source_url=EXCLUDED.official_source_url,
source_publisher=EXCLUDED.source_publisher,published=EXCLUDED.published,verified_at=EXCLUDED.verified_at;

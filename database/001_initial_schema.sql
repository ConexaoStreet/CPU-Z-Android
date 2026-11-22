-- CPU-Z Android: schema reprodutível (PostgreSQL 18 + Neon Auth + Data API).
-- Antes de aplicar em projeto novo, habilitar Neon Auth e Neon Data API no painel.
-- Nunca disponibilizar credenciais de Postgres no APK.
BEGIN;
CREATE TABLE IF NOT EXISTS public.devices(
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id text NOT NULL DEFAULT auth.user_id(),
  installation_id uuid NOT NULL,
  manufacturer text NOT NULL,
  model text NOT NULL,
  hardware text,
  android_version text,
  soc text,
  first_seen_at timestamptz NOT NULL DEFAULT now(),
  last_seen_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(id,owner_id), UNIQUE(owner_id,installation_id)
);
CREATE TABLE IF NOT EXISTS public.diagnostic_runs(
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id text NOT NULL DEFAULT auth.user_id(),
  device_id uuid NOT NULL,
  captured_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(),
  client_version text NOT NULL,
  schema_version integer NOT NULL DEFAULT 1,
  status text NOT NULL DEFAULT 'completed' CHECK (status IN ('completed','partial','inconclusive')),
  battery_temperature_c numeric(5,2),
  thermal_severity smallint,
  snapshot jsonb NOT NULL DEFAULT '{}'::jsonb CHECK(jsonb_typeof(snapshot)='object'),
  summary jsonb NOT NULL DEFAULT '{}'::jsonb CHECK(jsonb_typeof(summary)='object'),
  UNIQUE(id,owner_id),
  CONSTRAINT run_device_owner_fk FOREIGN KEY(device_id,owner_id)
    REFERENCES public.devices(id,owner_id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS public.component_observations(
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id text NOT NULL DEFAULT auth.user_id(),
  run_id uuid NOT NULL,
  category text NOT NULL,
  label text NOT NULL,
  reported_vendor text,
  reported_part_id text,
  source text NOT NULL,
  observation text NOT NULL CHECK(observation IN ('detected','not_detected','not_accessible','inconsistent','user_reported')),
  evidence jsonb NOT NULL DEFAULT '{}'::jsonb CHECK(jsonb_typeof(evidence)='object'),
  authenticity text NOT NULL DEFAULT 'not_verifiable' CHECK(authenticity='not_verifiable'),
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT observation_run_owner_fk FOREIGN KEY(run_id,owner_id)
    REFERENCES public.diagnostic_runs(id,owner_id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS public.test_results(
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id text NOT NULL DEFAULT auth.user_id(),
  run_id uuid NOT NULL,
  test_name text NOT NULL,
  status text NOT NULL CHECK(status IN ('pass','fail','inconclusive','not_tested')),
  method text NOT NULL CHECK(method IN ('automatic','guided_user_confirmation')),
  evidence jsonb NOT NULL DEFAULT '{}'::jsonb CHECK(jsonb_typeof(evidence)='object'),
  recorded_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT test_run_owner_fk FOREIGN KEY(run_id,owner_id)
    REFERENCES public.diagnostic_runs(id,owner_id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS public.telemetry_samples(
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  owner_id text NOT NULL DEFAULT auth.user_id(),
  device_id uuid NOT NULL,
  run_id uuid,
  metric text NOT NULL,
  value numeric NOT NULL,
  unit text NOT NULL,
  provenance text NOT NULL,
  captured_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT sample_device_owner_fk FOREIGN KEY(device_id,owner_id)
    REFERENCES public.devices(id,owner_id) ON DELETE CASCADE,
  CONSTRAINT sample_run_owner_fk FOREIGN KEY(run_id,owner_id)
    REFERENCES public.diagnostic_runs(id,owner_id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS public.report_records(
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id text NOT NULL DEFAULT auth.user_id(),
  run_id uuid NOT NULL,
  format text NOT NULL CHECK(format IN ('pdf','json')),
  sha256_hex text CHECK(sha256_hex IS NULL OR sha256_hex ~ '^[0-9a-f]{64}$'),
  storage_object_key text,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT report_run_owner_fk FOREIGN KEY(run_id,owner_id)
    REFERENCES public.diagnostic_runs(id,owner_id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS public.user_preferences(
  owner_id text PRIMARY KEY DEFAULT auth.user_id(),
  cloud_sync_enabled boolean NOT NULL DEFAULT false,
  anonymous_analytics_enabled boolean NOT NULL DEFAULT false,
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS public.hardware_catalog(
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  manufacturer text NOT NULL,
  model text NOT NULL,
  component_category text NOT NULL,
  expected_attributes jsonb NOT NULL DEFAULT '{}'::jsonb,
  official_source_url text,
  source_publisher text,
  published boolean NOT NULL DEFAULT false,
  verified_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(manufacturer,model,component_category)
);
CREATE SCHEMA IF NOT EXISTS private_cpu_z;
CREATE TABLE IF NOT EXISTS private_cpu_z.oem_attestations(
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  run_id uuid REFERENCES public.diagnostic_runs(id) ON DELETE CASCADE,
  component_category text NOT NULL,
  issuer text NOT NULL,
  verification_method text NOT NULL,
  signature_sha256 text NOT NULL CHECK(signature_sha256 ~ '^[0-9a-f]{64}$'),
  document_reference text,
  verified_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_runs_owner_date ON public.diagnostic_runs(owner_id,captured_at DESC);
CREATE INDEX IF NOT EXISTS idx_runs_device_date ON public.diagnostic_runs(device_id,captured_at DESC);
CREATE INDEX IF NOT EXISTS idx_tests_run ON public.test_results(run_id);
CREATE INDEX IF NOT EXISTS idx_components_run ON public.component_observations(run_id);
CREATE INDEX IF NOT EXISTS idx_samples_device_date ON public.telemetry_samples(device_id,captured_at DESC);
CREATE INDEX IF NOT EXISTS idx_catalog_model ON public.hardware_catalog(lower(manufacturer),lower(model));
CREATE INDEX IF NOT EXISTS idx_reports_run ON public.report_records(run_id);
ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.diagnostic_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.component_observations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.test_results ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.telemetry_samples ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.report_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.hardware_catalog ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.devices FORCE ROW LEVEL SECURITY;
ALTER TABLE public.diagnostic_runs FORCE ROW LEVEL SECURITY;
ALTER TABLE public.component_observations FORCE ROW LEVEL SECURITY;
ALTER TABLE public.test_results FORCE ROW LEVEL SECURITY;
ALTER TABLE public.telemetry_samples FORCE ROW LEVEL SECURITY;
ALTER TABLE public.report_records FORCE ROW LEVEL SECURITY;
ALTER TABLE public.user_preferences FORCE ROW LEVEL SECURITY;
ALTER TABLE public.hardware_catalog FORCE ROW LEVEL SECURITY;
CREATE POLICY devices_owner ON public.devices FOR ALL TO authenticated
  USING(owner_id=auth.user_id()) WITH CHECK(owner_id=auth.user_id());
CREATE POLICY runs_owner ON public.diagnostic_runs FOR ALL TO authenticated
  USING(owner_id=auth.user_id()) WITH CHECK(owner_id=auth.user_id());
CREATE POLICY observations_owner ON public.component_observations FOR ALL TO authenticated
  USING(owner_id=auth.user_id()) WITH CHECK(owner_id=auth.user_id());
CREATE POLICY tests_owner ON public.test_results FOR ALL TO authenticated
  USING(owner_id=auth.user_id()) WITH CHECK(owner_id=auth.user_id());
CREATE POLICY samples_owner ON public.telemetry_samples FOR ALL TO authenticated
  USING(owner_id=auth.user_id()) WITH CHECK(owner_id=auth.user_id());
CREATE POLICY reports_owner ON public.report_records FOR ALL TO authenticated
  USING(owner_id=auth.user_id()) WITH CHECK(owner_id=auth.user_id());
CREATE POLICY prefs_owner ON public.user_preferences FOR ALL TO authenticated
  USING(owner_id=auth.user_id()) WITH CHECK(owner_id=auth.user_id());
CREATE POLICY catalog_published_auth ON public.hardware_catalog FOR SELECT TO authenticated USING(published=true);
CREATE POLICY catalog_published_anon ON public.hardware_catalog FOR SELECT TO anonymous USING(published=true);
REVOKE ALL ON SCHEMA private_cpu_z FROM PUBLIC,authenticated,anonymous;
GRANT USAGE ON SCHEMA public TO authenticated,anonymous;
GRANT SELECT,INSERT,UPDATE,DELETE ON
  public.devices,public.diagnostic_runs,public.component_observations,
  public.test_results,public.telemetry_samples,public.report_records,public.user_preferences
TO authenticated;
GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO authenticated;
GRANT SELECT ON public.hardware_catalog TO authenticated,anonymous;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM anonymous;
COMMIT;

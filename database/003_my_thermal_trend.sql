-- RPC somente leitura; SECURITY INVOKER mantém RLS e filtros por usuário.
CREATE OR REPLACE FUNCTION public.my_thermal_trend(p_installation_id uuid)
RETURNS jsonb
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = public
AS $function$
 SELECT jsonb_build_object(
  'samples',count(r.id),
  'min_battery_c',min(r.battery_temperature_c),
  'avg_battery_c',round(avg(r.battery_temperature_c),1),
  'max_battery_c',max(r.battery_temperature_c),
  'last_reading_at',max(r.captured_at),
  'severe_thermal_events',count(*) FILTER (WHERE r.thermal_severity>=3),
  'interpretation','Descritivo: a temperatura varia com uso e ambiente; este relatório não comprova defeito nem originalidade.'
 )
 FROM public.devices d
 LEFT JOIN public.diagnostic_runs r ON r.device_id=d.id AND r.owner_id=d.owner_id
 WHERE d.owner_id=auth.user_id()
   AND d.installation_id=p_installation_id
$function$;
REVOKE ALL ON FUNCTION public.my_thermal_trend(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.my_thermal_trend(uuid) TO authenticated;

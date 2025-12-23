#!/usr/bin/env python3

"""
Flow Recording Parser

Converts ADB logcat output from FlowMonitor to structured JSON
and optionally generates Kotlin test fixtures.

Usage:
    python3 parse_flows.py input.txt output.json
    python3 parse_flows.py input.txt output.json --generate-fixture
"""

import re
import json
import sys
from pathlib import Path
from datetime import datetime
from typing import List, Dict, Any
from collections import defaultdict


def parse_logcat_line(line: str) -> Dict[str, Any] | None:
    """
    Parse a single logcat line containing a FLOW_EVENT

    Example input:
    12-15 23:31:39.730  2459  2459 D FlowRecorder: FLOW_EVENT:{"flow":"cardState","timestamp":1702683099730,"value":"Loading"}

    Returns: Parsed JSON dict or None if not a flow event
    """
    # Look for FLOW_EVENT: followed by JSON
    match = re.search(r'FLOW_EVENT:({.*})', line)
    if not match:
        return None

    try:
        json_str = match.group(1)
        return json.loads(json_str)
    except json.JSONDecodeError as e:
        print(f"Warning: Failed to parse JSON: {e}", file=sys.stderr)
        print(f"  Line: {line.strip()}", file=sys.stderr)
        return None


def parse_logcat_file(logcat_path: Path) -> List[Dict[str, Any]]:
    """
    Parse entire logcat file and extract all flow emissions

    Returns: List of emission events
    """
    emissions = []

    with open(logcat_path, 'r', encoding='utf-8') as f:
        for line_num, line in enumerate(f, 1):
            event = parse_logcat_line(line)
            if event:
                event['_logLine'] = line_num  # Add line number for debugging
                emissions.append(event)

    return emissions


def group_emissions_by_flow(emissions: List[Dict[str, Any]]) -> Dict[str, List[Dict[str, Any]]]:
    """
    Group emissions by flow name

    Returns: Dict mapping flow name to list of its emissions
    """
    grouped = defaultdict(list)

    for emission in emissions:
        flow_name = emission.get('flow', 'unknown')
        grouped[flow_name].append(emission)

    return dict(grouped)


def calculate_statistics(emissions: List[Dict[str, Any]]) -> Dict[str, Any]:
    """
    Calculate useful statistics about the recording session
    """
    if not emissions:
        return {}

    grouped = group_emissions_by_flow(emissions)
    timestamps = [e['timestamp'] for e in emissions]

    return {
        'totalEmissions': len(emissions),
        'flowCount': len(grouped),
        'emissionsByFlow': {name: len(events) for name, events in grouped.items()},
        'durationMs': max(timestamps) - min(timestamps) if timestamps else 0,
        'firstEmission': min(timestamps) if timestamps else None,
        'lastEmission': max(timestamps) if timestamps else None,
    }


def create_recording_json(emissions: List[Dict[str, Any]], session_id: str) -> Dict[str, Any]:
    """
    Create the final JSON structure for the recording
    """
    stats = calculate_statistics(emissions)

    return {
        'meta': {
            'sessionId': session_id,
            'recordedAt': datetime.now().isoformat(),
            'tool': 'flow-recorder',
            'version': '1.0',
        },
        'statistics': stats,
        'emissions': emissions,
    }


def generate_kotlin_fixture(recording_data: Dict[str, Any], output_path: Path) -> None:
    """
    Generate a Kotlin test fixture file from recorded data

    This creates a simple fixture - you may want to customize this
    based on your specific state types.
    """

    session_id = recording_data['meta']['sessionId']
    recorded_at = recording_data['meta']['recordedAt']
    emissions = recording_data['emissions']

    # Group by flow for cleaner organization
    grouped = group_emissions_by_flow(emissions)

    fixture_code = f'''// Auto-generated test fixture
// Session: {session_id}
// Recorded: {recorded_at}
// DO NOT EDIT - Regenerate by recording a new session

package protect.card_locker.fixtures

/**
 * Recorded flow emissions from a real user interaction session
 *
 * Statistics:
 * - Total emissions: {recording_data['statistics']['totalEmissions']}
 * - Duration: {recording_data['statistics']['durationMs']}ms
 * - Flows captured: {', '.join(grouped.keys())}
 */
object {session_id.replace('-', '_').title()}Fixture {{

    // Raw emission data as JSON strings
    // Parse these in your tests to recreate the flow sequence
    val emissions = """
{json.dumps(emissions, indent=8)}
    """.trimIndent()

'''

    # Add convenience accessors for each flow
    for flow_name, flow_emissions in grouped.items():
        fixture_code += f'''
    // {len(flow_emissions)} emissions from {flow_name}
    val {flow_name}Emissions = listOf(
'''
        for i, emission in enumerate(flow_emissions):
            value = emission['value']
            timestamp = emission['timestamp']
            comma = ',' if i < len(flow_emissions) - 1 else ''
            
            if isinstance(value, (dict, list)):
                # Serialize complex objects to JSON strings for Kotlin parsing
                json_val = json.dumps(value)
                fixture_code += f'        """{json_val}"""{comma}  // t={timestamp}\n'
            else:
                fixture_code += f'        "{value}"{comma}  // t={timestamp}\n'

        fixture_code += '    )\n'

    fixture_code += '}\n'

    with open(output_path, 'w', encoding='utf-8') as f:
        f.write(fixture_code)


def main():
    """Main entry point"""

    if len(sys.argv) < 3:
        print("Usage: python3 parse_flows.py <input.txt> <output.json> [--generate-fixture]")
        sys.exit(1)

    input_path = Path(sys.argv[1])
    output_path = Path(sys.argv[2])
    generate_fixture = '--generate-fixture' in sys.argv

    if not input_path.exists():
        print(f"Error: Input file not found: {input_path}")
        sys.exit(1)

    print(f"📖 Reading logcat from: {input_path}")

    # Parse logcat
    emissions = parse_logcat_file(input_path)

    if not emissions:
        print("❌ No flow emissions found in logcat")
        print("\nTroubleshooting:")
        print("  1. Check if FlowMonitor.kt is integrated")
        print("  2. Verify .monitor() is called on flows")
        print("  3. Ensure you interacted with monitored features")
        sys.exit(1)

    session_id = output_path.stem
    recording_data = create_recording_json(emissions, session_id)

    # Write JSON
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(recording_data, f, indent=2)

    # Print summary
    stats = recording_data['statistics']
    print(f"✅ Parsed {stats['totalEmissions']} emissions from {stats['flowCount']} flows")
    print(f"📊 Duration: {stats['durationMs']}ms")
    print(f"💾 Saved to: {output_path}")

    # Generate Kotlin fixture if requested
    if generate_fixture:
        fixture_path = output_path.with_suffix('.kt')
        generate_kotlin_fixture(recording_data, fixture_path)
        print(f"🔧 Generated Kotlin fixture: {fixture_path}")
        print(f"\n   Copy this to: app/src/test/java/protect/card_locker/fixtures/")

    # Show emission breakdown
    print("\n📋 Emissions by flow:")
    for flow_name, count in stats['emissionsByFlow'].items():
        print(f"   {flow_name}: {count} emissions")


if __name__ == '__main__':
    main()

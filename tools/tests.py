import subprocess, sys, unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SCRIPT=ROOT/"tools"/"project_status.py"
class TrackingSmokeTest(unittest.TestCase):
    def run_cli(self,*args,cwd=None):
        return subprocess.run([sys.executable,str(SCRIPT),*args],cwd=cwd or ROOT,text=True,capture_output=True)
    def test_validate_from_repo(self): self.assertEqual(0,self.run_cli("validate").returncode)
    def test_validate_from_other_directory(self): self.assertEqual(0,self.run_cli("validate",cwd=ROOT.parent).returncode)
    def test_next_reports_without_error(self):
        result=self.run_cli("next"); self.assertEqual(0,result.returncode); self.assertTrue(result.stdout.strip())
    def test_unknown_task_is_rejected(self): self.assertNotEqual(0,self.run_cli("claim","T99","--owner","test").returncode)
if __name__ == "__main__": unittest.main()

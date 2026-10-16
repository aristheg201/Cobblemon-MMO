#!/usr/bin/env python3
"""Reproducible local patches against the exact user-supplied binaries. Never runs CI."""
import argparse, hashlib, json, os, subprocess, tempfile, zipfile
from pathlib import Path

INPUTS = {
    'quest': ('6b849290d3e36c6c6dd548531561cb320211b658ef3514de9ce0be5f59a49134',
              'com/cobblemon/mdks/cobblepass/patch/QuestEngine.class', 'SafeQuestStore',
              '2.1.0+server.gui.6', 'CobblePass-2.1.0-server-gui-6.jar'),
    'guild': ('7f7353f66cf9bb258522fec3a5e0309c84972cf62ccbc6da2023e4b8e1c715a3',
              'vn/guild/core/GuildEngine.class', 'GuildMiningFilters',
              '1.1.0+teamfix-masterspawnfix2-miningfix1', 'guild-progression-1.1.0-mining-fix-1.jar')}

def normalize_tags(value):
    if isinstance(value, dict):
        for key, child in value.items():
            if key == 'tags':
                def norm(s):
                    ores = ('coal', 'copper', 'iron', 'gold', 'lapis', 'redstone', 'emerald', 'diamond')
                    return s + 's' if s in ['minecraft:' + ore + '_ore' for ore in ores] else s
                value[key] = [norm(s) for s in child] if isinstance(child, list) else norm(child)
            elif key == 'currency' and child == 'beastcoin': value[key]='secondary'
            else: normalize_tags(child)
    elif isinstance(value, list):
        for child in value: normalize_tags(child)

def main():
    p=argparse.ArgumentParser();p.add_argument('--cobblepass',type=Path,required=True)
    p.add_argument('--guild',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    p.add_argument('--contracts',type=Path);p.add_argument('--jdk',type=Path,default=Path(os.environ.get('JAVA_HOME','/workspace/tools/jdk-21.0.2')))
    p.add_argument('--gradle-lib',type=Path,default=Path('/workspace/tools/gradle-8.8/lib'))
    a=p.parse_args();root=Path(__file__).resolve().parent;a.output.mkdir(parents=True,exist_ok=True)
    libs=[a.gradle_lib/n for n in ('gson-2.10.jar','asm-9.7.jar','asm-tree-9.7.jar')]
    cp=os.pathsep.join(map(str,libs));report={}
    with tempfile.TemporaryDirectory() as temp:
        temp=Path(temp);classes=temp/'classes';classes.mkdir()
        subprocess.run([str(a.jdk/'bin/javac'),'--release','21','-cp',cp,'-d',str(classes),
                        *map(str,(root/'src').rglob('*.java'))],check=True)
        for kind,source in [('quest',a.cobblepass),('guild',a.guild)]:
            expected,class_path,helper,version,name=INPUTS[kind]
            digest=hashlib.sha256(source.read_bytes()).hexdigest()
            if digest != expected:raise SystemExit(f'Unsupported {kind} input SHA256: {digest}')
            with zipfile.ZipFile(source) as z:files={n:z.read(n) for n in z.namelist()}
            original=temp/(kind+'.class');original.write_bytes(files[class_path]);patched=temp/(kind+'-patched.class')
            subprocess.run([str(a.jdk/'bin/java'),'-cp',str(classes)+os.pathsep+cp,'PatchClasses',kind,str(original),str(patched)],check=True)
            files[class_path]=patched.read_bytes()
            helper_path=f'vn/svframe/compat/{helper}.class';files[helper_path]=(classes/helper_path).read_bytes()
            if kind=='guild':
                economy='vn/guild/runtime/Economy.class';original.write_bytes(files[economy])
                subprocess.run([str(a.jdk/'bin/java'),'-cp',str(classes)+os.pathsep+cp,'PatchClasses','currency',str(original),str(patched)],check=True)
                files[economy]=patched.read_bytes()
                currency='vn/svframe/compat/GuildCurrencyIds.class';files[currency]=(classes/currency).read_bytes()
            descriptor=json.loads(files['fabric.mod.json']);descriptor['version']=version
            if kind=='quest':descriptor.setdefault('depends',{})['gooeylibs']='>=3.1.0'
            files['fabric.mod.json']=(json.dumps(descriptor,ensure_ascii=False,indent=2)+'\n').encode()
            if kind=='guild':
                config=json.loads(files['defaults/guild.json']);normalize_tags(config)
                files['defaults/guild.json']=(json.dumps(config,ensure_ascii=False,indent=2)+'\n').encode()
            receipt={'input_sha256':digest,'modified_class':class_path,'helper':helper_path,'patch_version':version}
            if kind=='guild':receipt['additional_modified_classes']=['vn/guild/runtime/Economy.class'];receipt['currency_policy']='Valid configured IDs preserved; BEconomy namespace accepted; absent beastcoin display alias resolves only to existing secondary.'
            files['META-INF/svframe-compat-patch.json']=(json.dumps(receipt,indent=2)+'\n').encode()
            output=a.output/name
            with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as z:
                for n,data in sorted(files.items()):
                    entry=zipfile.ZipInfo(n,date_time=(1980,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;z.writestr(entry,data)
            report[kind]={**receipt,'artifact':name,'sha256':hashlib.sha256(output.read_bytes()).hexdigest()}
        if a.contracts:
            config=json.loads(a.contracts.read_text());normalize_tags(config)
            (a.output/'guild-contracts-fixed.json').write_text(json.dumps(config,ensure_ascii=False,indent=2)+'\n')
        # Standalone tests use the real patched GuildEngine, not a replacement engine.
        test_classes=temp/'test-classes';test_classes.mkdir()
        test_cp=os.pathsep.join([str(classes),cp,str(a.output/INPUTS['guild'][4])])
        subprocess.run([str(a.jdk/'bin/javac'),'--release','21','-cp',test_cp,'-d',str(test_classes),
                        *map(str,(root/'test').glob('*.java'))],check=True)
        subprocess.run([str(a.jdk/'bin/java'),'-ea','-cp',str(test_classes)+os.pathsep+test_cp,
                        'CompatibilityRegression',str(a.output/'guild-contracts-fixed.json'),str(a.output/'compat-tests.json')],check=True)
    (a.output/'patch-build.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report,indent=2))

if __name__=='__main__':main()

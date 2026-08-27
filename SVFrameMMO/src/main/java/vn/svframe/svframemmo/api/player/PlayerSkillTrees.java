package vn.svframe.svframemmo.api.player;

import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.skilltree.NodeIncrementResult;
import vn.svframe.svframemmo.skilltree.NodeState;
import vn.svframe.svframemmo.skilltree.ParentType;
import vn.svframe.svframemmo.skilltree.SkillTree;
import vn.svframe.svframemmo.skilltree.SkillTreeNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Persistent skill-tree progress and source-equivalent node state resolution. */
public final class PlayerSkillTrees {
    private final PlayerData data;
    private final Map<String, Integer> points = new LinkedHashMap<>();
    private final Map<String, Integer> nodeLevels = new LinkedHashMap<>();
    private final Map<String, NodeState> states = new LinkedHashMap<>();
    private final Map<String, Integer> spentByTree = new LinkedHashMap<>();

    public PlayerSkillTrees(PlayerData data) { this.data = data; }

    public int getPoints(String treeId) { return points.getOrDefault(normalize(treeId), 0); }
    public void setPoints(String treeId, int value) { String key=normalize(treeId); if(value<=0) points.remove(key); else points.put(key,value); }
    public void givePoints(String treeId, int value) { setPoints(treeId, Math.max(0, getPoints(treeId) + value)); }
    public int getNodeLevel(SkillTreeNode node) { return nodeLevels.getOrDefault(node.getFullId(), 0); }
    public NodeState getNodeState(SkillTreeNode node) { return states.get(node.getFullId()); }
    public int getPointsSpent(SkillTree tree) { return spentByTree.getOrDefault(tree.getId(), 0); }
    public Map<String,Integer> pointMap(){return Map.copyOf(points);} public Map<String,Integer> nodeLevelMap(){return Map.copyOf(nodeLevels);}

    public void setNodeLevel(SkillTreeNode node, int value) {
        int next=Math.max(0,Math.min(node.getMaxLevel(),value)); int old=getNodeLevel(node);
        if(next==0)nodeLevels.remove(node.getFullId()); else nodeLevels.put(node.getFullId(),next);
        int delta=(next-old)*node.getPointConsumption(); spentByTree.merge(node.getTree().getId(),delta,Integer::sum);
        if(spentByTree.getOrDefault(node.getTree().getId(),0)<=0)spentByTree.remove(node.getTree().getId());
    }

    public NodeIncrementResult canIncrement(SkillTreeNode node) {
        NodeState state=getNodeState(node); if(state==null){node.getTree().resolveStates(data);state=getNodeState(node);}
        if(state==NodeState.MAXED_OUT)return NodeIncrementResult.MAX_LEVEL_REACHED;
        if(state!=NodeState.UNLOCKED&&state!=NodeState.UNLOCKABLE)return NodeIncrementResult.LOCKED_NODE;
        if(!node.hasPermissionRequirement(data))return NodeIncrementResult.PERMISSION_DENIED;
        int available=getPoints(node.getTree().getId())+getPoints("global");
        if(available<node.getPointConsumption())return NodeIncrementResult.NOT_ENOUGH_POINTS;
        return NodeIncrementResult.SUCCESS;
    }

    public NodeIncrementResult increment(SkillTreeNode node) {
        NodeIncrementResult result=canIncrement(node); if(result!=NodeIncrementResult.SUCCESS)return result;
        int newLevel=getNodeLevel(node)+1; setNodeLevel(node,newLevel);
        SVFrameMMO.experienceTables().claim(node.getExperienceTable(),node.getKey(),data,newLevel);
        int cost=node.getPointConsumption(); int local=Math.min(getPoints(node.getTree().getId()),cost);
        if(local>0)givePoints(node.getTree().getId(),-local); if(cost>local)givePoints("global",-(cost-local));
        resolveStates(node.getTree()); return NodeIncrementResult.SUCCESS;
    }

    public boolean reallocate(SkillTree tree) {
        int spent=getPointsSpent(tree); if(spent<1||data.getSkillTreeReallocationPoints()<1)return false;
        givePoints(tree.getId(),spent); data.giveSkillTreeReallocationPoints(-1); reset(tree,true); return true;
    }

    public void reset(SkillTree tree, boolean resetClaims) {
        for(SkillTreeNode node:tree.getNodes()){
            SVFrameMMO.experienceTables().unclaim(node.getExperienceTable(),node.getKey(),data,resetClaims);
            setNodeLevel(node,0);
        }
        resolveStates(tree);
    }

    public void resetAll(boolean resetClaims) {
        for(String treeId:data.getProfess().getSkillTreeIds()){
            SkillTree tree=SVFrameMMO.skillTrees().get(treeId); if(tree!=null)reset(tree,resetClaims);
        }
        points.clear(); states.clear(); spentByTree.clear();
    }

    public void resolveClassTrees(){for(String id:data.getProfess().getSkillTreeIds()){SkillTree tree=SVFrameMMO.skillTrees().get(id);if(tree!=null)resolveStates(tree);}}
    public void resolveStates(SkillTree tree) {
        for(SkillTreeNode node:tree.getNodes())states.remove(node.getFullId());
        boolean locked=getPointsSpent(tree)>=tree.getMaxPointSpent(); NodeState lockState=locked?NodeState.FULLY_LOCKED:NodeState.LOCKED;
        for(SkillTreeNode node:tree.getNodes()){int level=getNodeLevel(node);states.put(node.getFullId(),level==0?lockState:level==node.getMaxLevel()?NodeState.MAXED_OUT:NodeState.UNLOCKED);}
        if(locked)return;
        Deque<SkillTreeNode> unreachable=new ArrayDeque<>();
        for(SkillTreeNode node:tree.getNodes()){
            for(var edge:node.getParents())if(edge.getType()==ParentType.INCOMPATIBLE&&getNodeState(edge.getParent()).isUnlocked()){unreachable.add(node);break;}
            int max=node.getMaxChildren(); if(max>0){int unlocked=0;var lockedChildren=new java.util.ArrayList<SkillTreeNode>();for(var edge:node.getChildren())switch(getNodeState(edge.getChild())){case LOCKED->lockedChildren.add(edge.getChild());case MAXED_OUT,UNLOCKED->unlocked++;default->{}}if(unlocked>=max)unreachable.addAll(lockedChildren);}
        }
        Set<SkillTreeNode> checked=new HashSet<>(); while(!unreachable.isEmpty()){SkillTreeNode node=unreachable.pop();if(!checked.add(node))continue;states.put(node.getFullId(),NodeState.FULLY_LOCKED);for(var edge:node.getChildren())if(edge.getType()==ParentType.STRONG&&!checked.contains(edge.getChild())&&isUnreachable(edge.getChild()))unreachable.push(edge.getChild());}
        outer: for(SkillTreeNode node:tree.getNodes())if(getNodeState(node)==NodeState.LOCKED){if(node.isRoot()){states.put(node.getFullId(),NodeState.UNLOCKABLE);continue;}boolean soft=false,hasSoft=false;for(var edge:node.getParents()){if(edge.getType()==ParentType.STRONG&&getNodeLevel(edge.getParent())<edge.getLevel())continue outer;if(!soft&&edge.getType()==ParentType.SOFT){hasSoft=true;if(getNodeLevel(edge.getParent())>=edge.getLevel())soft=true;}}if(!hasSoft||soft)states.put(node.getFullId(),NodeState.UNLOCKABLE);}
    }
    private boolean isUnreachable(SkillTreeNode node){boolean soft=false,hasSoft=false;for(var edge:node.getParents()){if(edge.getType()==ParentType.STRONG&&getNodeState(edge.getParent())==NodeState.FULLY_LOCKED)return true;if(!soft&&edge.getType()==ParentType.SOFT){hasSoft=true;if(getNodeState(edge.getParent())!=NodeState.FULLY_LOCKED)soft=true;}}return hasSoft&&!soft;}

    public void restore(Map<String,? extends Number> pointMap,Map<String,? extends Number> nodeMap){points.clear();nodeLevels.clear();spentByTree.clear();states.clear();if(pointMap!=null)pointMap.forEach((k,v)->{if(k!=null&&v!=null&&v.intValue()>0)points.put(normalize(k),v.intValue());});if(nodeMap!=null)nodeMap.forEach((fullId,v)->{if(fullId==null||v==null)return;SkillTreeNode node=SVFrameMMO.skillTrees().findNode(fullId);if(node!=null)setNodeLevel(node,v.intValue());});resolveClassTrees();}
    public void applyTemporary(){for(String treeId:data.getProfess().getSkillTreeIds()){SkillTree tree=SVFrameMMO.skillTrees().get(treeId);if(tree==null)continue;for(SkillTreeNode node:tree.getNodes())SVFrameMMO.experienceTables().applyTemporary(node.getExperienceTable(),node.getKey(),data);}}
    private static String normalize(String v){return v==null?"global":v.trim().toLowerCase(java.util.Locale.ROOT).replace('_','-').replace(' ','-');}
}
